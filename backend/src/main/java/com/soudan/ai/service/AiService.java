package com.soudan.ai.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.soudan.ai.domain.AiUsage;
import com.soudan.ai.llm.LlmClient;
import com.soudan.ai.llm.LlmClient.Turn;
import com.soudan.ai.repository.AiUsageRepository;
import com.soudan.calendar.CalendarApi;
import com.soudan.chat.ChatApi;
import com.soudan.common.ai.KnowledgeDoc;
import com.soudan.common.ai.KnowledgeSource;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.report.ReportApi;
import com.soudan.task.TaskApi;

/**
 * Trợ lý AI (RAG). Mỗi lượt: (1) lấy dữ liệu NGƯỜI HỎI được phép xem từ các module sở hữu, (2) chọn những mẩu liên quan,
 * (3) dựng prompt có đánh số tài liệu, (4) gọi mô hình, (5) trả lời kèm các nguồn được trích dẫn. AI không tự đọc bảng
 * của module khác, không ghi gì vào hệ thống, và mỗi người bị giới hạn số lượt mỗi giờ. Admin không dùng trợ lý.
 */
@Service
public class AiService {

	private static final Pattern CITE = Pattern.compile("\\[(\\d{1,3})]");
	private static final int MAX_HISTORY = 6;
	private static final int MAX_QUESTION = 1000;
	private static final int MAX_TEXT = 4000;
	private static final int POOL_PER_SOURCE = 120;

	public record Source(int n, String kind, String title, String path, Instant at) {
	}

	public record Answer(String text, List<Source> sources) {
	}

	/** Một lượt cũ trong hội thoại hỏi đáp (client gửi lại để câu hỏi nối tiếp hiểu ngữ cảnh). */
	public record HistoryTurn(String role, String text) {
	}

	public enum RewriteMode {
		POLISH, FORMAL, SHORTEN, TRANSLATE_VI, TRANSLATE_JA, TRANSLATE_EN
	}

	private final AiProperties props;
	private final LlmClient llm;
	private final Retriever retriever;
	private final PromptBuilder prompts;
	private final List<KnowledgeSource> sources;
	private final ReportApi reports;
	private final TaskApi tasks;
	private final ChatApi chats;
	private final CalendarApi calendar;
	private final AiUsageRepository usage;
	private final CurrentUser currentUser;
	private final AccessPolicy policy;
	private final Clock clock;
	private final ZoneId zone;

	public AiService(AiProperties props, LlmClient llm, Retriever retriever, PromptBuilder prompts,
			List<KnowledgeSource> sources, ReportApi reports, TaskApi tasks, ChatApi chats, CalendarApi calendar,
			AiUsageRepository usage, CurrentUser currentUser, AccessPolicy policy, Clock clock, ZoneId zone) {
		this.props = props;
		this.llm = llm;
		this.retriever = retriever;
		this.prompts = prompts;
		this.sources = sources;
		this.reports = reports;
		this.tasks = tasks;
		this.chats = chats;
		this.calendar = calendar;
		this.usage = usage;
		this.currentUser = currentUser;
		this.policy = policy;
		this.clock = clock;
		this.zone = zone;
	}

	/** Trợ lý đã được cấu hình chưa (có khoá API hoặc đang ở chế độ giả lập). */
	public boolean enabled() {
		return props.isConfigured();
	}

	// ---- 2. Hỏi đáp (RAG) ----

	public Answer ask(String question, List<HistoryTurn> history, String lang) {
		String q = clean(question, MAX_QUESTION, "Hãy nhập câu hỏi");
		Long me = begin();
		Instant since = clock.instant().minus(Duration.ofDays(props.retrievalDays()));

		List<KnowledgeDoc> pool = new ArrayList<>();
		for (KnowledgeSource source : sources) {
			pool.addAll(source.visibleSince(since, POOL_PER_SOURCE));
		}
		List<KnowledgeDoc> docs = new ArrayList<>(retriever.top(q, pool, props.topDocs(), props.retrievalDays()));
		// Luôn kèm "bức tranh của tôi": việc đang mở và ghi chú sắp tới, vì câu hỏi như "việc nào sắp trễ" không có từ khoá
		addAll(docs, tasks.myOpenTasks(10));
		addAll(docs, calendar.myNotes(today(), today().plusDays(14)));

		List<Turn> turns = new ArrayList<>();
		int from = Math.max(0, history == null ? 0 : history.size() - MAX_HISTORY);
		if (history != null) {
			for (HistoryTurn h : history.subList(from, history.size())) {
				if (h.text() != null && !h.text().isBlank()) {
					turns.add(new Turn("assistant".equals(h.role()) ? "assistant" : "user",
							PromptBuilder.text(trim(h.text(), MAX_QUESTION * 2))));
				}
			}
		}
		List<KnowledgeDoc> used = fit(docs);
		turns.add(new Turn("user", prompts.documents(used) + "\nCâu hỏi: " + PromptBuilder.text(q)));
		return generate(me, "ASK", lang, turns, used);
	}

	// ---- 1. Tóm tắt báo cáo cộng đồng ----

	public Answer summarizeCommunity(Long communityId, LocalDate from, LocalDate to, String lang) {
		Long me = begin();
		LocalDate end = to == null ? today() : to;
		LocalDate start = from == null ? end.minusDays(6) : from;
		if (start.isAfter(end) || Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > 92) {
			throw new ValidationException("Khoảng ngày không hợp lệ (tối đa 92 ngày)");
		}
		ReportApi.CommunityDigest digest = reports.communityPosts(communityId, start, end, 80);
		if (digest.posts().isEmpty()) {
			throw new BusinessException(ErrorCode.AI_NO_DATA, "Không có báo cáo nào trong khoảng " + start + " đến " + end);
		}
		List<KnowledgeDoc> used = fit(digest.posts());
		String task = """
				Tóm tắt các báo cáo của cộng đồng "%s" từ %s đến %s cho người quản lý, gồm các phần:
				1) Tổng quan: 2-3 câu về tình hình chung.
				2) Từng người: mỗi người một gạch đầu dòng nêu họ đã làm gì.
				3) Vướng mắc và rủi ro cần hỗ trợ (nếu có), nêu rõ của ai.
				4) Việc tiếp theo được nhắc tới.
				Chỉ dùng nội dung các báo cáo bên dưới.""".formatted(PromptBuilder.text(digest.communityName()), start, end);
		return generate(me, "SUM_COMM", lang, List.of(new Turn("user", prompts.documents(used) + "\n" + task)), used);
	}

	// ---- 4. Tóm tắt cuộc trò chuyện ----

	public Answer summarizeChat(Long conversationId, int hours, String lang) {
		Long me = begin();
		int window = Math.min(Math.max(hours, 1), 24 * 14);
		KnowledgeDoc transcript = chats.transcript(conversationId, clock.instant().minus(Duration.ofHours(window)), 400);
		if (transcript.text() == null || transcript.text().isBlank()) {
			throw new BusinessException(ErrorCode.AI_NO_DATA, "Không có tin nhắn nào trong " + window + " giờ qua");
		}
		List<KnowledgeDoc> used = fit(List.of(transcript));
		String task = """
				Tóm tắt cuộc trò chuyện "%s" trong %d giờ qua cho người chưa đọc: các chủ đề chính, quyết định đã chốt,
				việc cần làm (nêu ai phụ trách nếu có), câu hỏi còn bỏ ngỏ. Ngắn gọn, theo gạch đầu dòng.""".formatted(
				PromptBuilder.text(transcript.title()), window);
		return generate(me, "SUM_CHAT", lang, List.of(new Turn("user", prompts.documents(used) + "\n" + task)), used);
	}

	// ---- 3. Hỗ trợ viết ----

	/** Soạn nháp báo cáo hằng ngày từ việc mình vừa làm. Người dùng tự sửa và đăng; AI không đăng thay. */
	public Answer draftReport(String lang) {
		Long me = begin();
		Instant since = clock.instant().minus(Duration.ofHours(48));
		List<KnowledgeDoc> docs = new ArrayList<>();
		addAll(docs, tasks.myTasksChangedSince(since, 30));
		List<KnowledgeDoc> open = tasks.myOpenTasks(15);
		addAll(docs, open);
		addAll(docs, calendar.myNotes(today(), today().plusDays(3)));
		if (docs.isEmpty()) {
			throw new BusinessException(ErrorCode.AI_NO_DATA, "Chưa có công việc hay ghi chú nào để soạn báo cáo");
		}
		List<KnowledgeDoc> used = fit(docs);
		String task = """
				Soạn nháp báo cáo hằng ngày của tôi gồm ba phần: "Đã làm", "Kế hoạch tiếp theo", "Vướng mắc".
				"Đã làm" lấy từ các việc có trạng thái thay đổi gần đây; "Kế hoạch tiếp theo" từ việc đang mở theo hạn; "Vướng mắc"
				từ lý do trả lại hoặc việc quá hạn. Viết ở ngôi thứ nhất, ngắn gọn. Chỗ nào chưa chắc chắn thì ghi "[cần bổ sung]".
				Chỉ dùng dữ liệu bên dưới.""";
		return generate(me, "DRAFT", lang, List.of(new Turn("user", prompts.documents(used) + "\n" + task)), used);
	}

	/** Viết lại, rút gọn hoặc dịch một đoạn chữ của chính người dùng. Không dùng dữ liệu hệ thống. */
	public Answer rewrite(String text, RewriteMode mode, String lang) {
		String body = clean(text, MAX_TEXT, "Hãy nhập nội dung cần xử lý");
		Long me = begin();
		String instruction = switch (mode) {
			case POLISH -> "Chỉnh sửa đoạn văn trong thẻ <text> cho rõ ràng, mạch lạc, đúng chính tả, giữ nguyên ý và ngôn ngữ gốc.";
			case FORMAL -> "Viết lại đoạn văn trong thẻ <text> theo giọng trang trọng, lịch sự, phù hợp công việc, giữ nguyên ý và ngôn ngữ gốc.";
			case SHORTEN -> "Rút gọn đoạn văn trong thẻ <text> còn khoảng một nửa độ dài nhưng giữ đủ ý chính, giữ nguyên ngôn ngữ gốc.";
			case TRANSLATE_VI -> "Dịch đoạn văn trong thẻ <text> sang tiếng Việt tự nhiên.";
			case TRANSLATE_JA -> "Dịch đoạn văn trong thẻ <text> sang tiếng Nhật tự nhiên, lịch sự (です・ます).";
			case TRANSLATE_EN -> "Dịch đoạn văn trong thẻ <text> sang tiếng Anh tự nhiên.";
		};
		String task = instruction + " Chỉ trả về đoạn văn kết quả, không giải thích, không thêm lời dẫn.";
		return generate(me, "REWRITE", lang, List.of(new Turn("user", prompts.userText(body) + "\n" + task)), List.of());
	}

	// ---- 5. Kế hoạch làm việc ----

	public Answer plan(int days, String lang) {
		Long me = begin();
		int horizon = Math.min(Math.max(days, 1), 14);
		LocalDate today = today();
		List<KnowledgeDoc> docs = new ArrayList<>();
		addAll(docs, tasks.myOpenTasks(40));
		addAll(docs, calendar.myNotes(today, today.plusDays(horizon)));
		if (docs.isEmpty()) {
			throw new BusinessException(ErrorCode.AI_NO_DATA, "Bạn chưa có việc đang mở hay ghi chú lịch nào");
		}
		List<KnowledgeDoc> used = fit(docs);
		String task = """
				Lập kế hoạch làm việc cho %d ngày tới (từ %s) của tôi. Trước hết nêu các việc đã QUÁ HẠN và việc SẮP đến hạn
				(so với hôm nay). Sau đó gợi ý thứ tự làm theo từng ngày, ưu tiên việc hạn gần và độ ưu tiên cao, tránh dồn việc
				vào ngày có nhiều ghi chú lịch. Việc không có hạn thì xếp vào chỗ còn trống. Ngắn gọn theo từng ngày.""".formatted(horizon, today);
		return generate(me, "PLAN", lang, List.of(new Turn("user", prompts.documents(used) + "\n" + task)), used);
	}

	// ---- nội bộ ----

	/** Hôm nay theo múi giờ nghiệp vụ (không phải UTC), khớp với ngày của báo cáo và lịch. */
	private LocalDate today() {
		return LocalDate.now(clock.withZone(zone));
	}

	/** Kiểm tra được dùng, rồi ghi một lượt vào nhật ký (tính cả lượt lỗi để chặn lạm dụng). Trả về id người dùng. */
	private Long begin() {
		if (policy.isAdmin()) {
			throw new ForbiddenException("Admin không dùng trợ lý AI");
		}
		if (!props.isConfigured()) {
			throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
		}
		Long me = currentUser.require().id();
		long recent = usage.countByUserIdAndCreatedAtAfter(me, clock.instant().minus(Duration.ofHours(1)));
		if (recent >= props.requestsPerHour()) {
			throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
		}
		return me;
	}

	private Answer generate(Long me, String kind, String lang, List<Turn> turns, List<KnowledgeDoc> used) {
		usage.save(new AiUsage(me, kind, used.size(), clock.instant()));
		String text = llm.complete(prompts.system(lang), turns, props.maxTokens());
		return new Answer(text, cited(text, used));
	}

	/** Chỉ trả về các nguồn mà câu trả lời thật sự trích dẫn (số không có trong danh sách bị bỏ qua). */
	static List<Source> cited(String text, List<KnowledgeDoc> used) {
		Set<Integer> numbers = new TreeSet<>();
		Matcher m = CITE.matcher(text);
		while (m.find()) {
			int n = Integer.parseInt(m.group(1));
			if (n >= 1 && n <= used.size()) {
				numbers.add(n);
			}
		}
		List<Source> result = new ArrayList<>();
		for (int n : numbers) {
			KnowledgeDoc d = used.get(n - 1);
			result.add(new Source(n, d.kind(), d.title(), d.path(), d.at()));
		}
		return result;
	}

	/** Bỏ trùng (cùng loại và id), rồi cắt cho vừa tổng số ký tự cho phép, giữ thứ tự ưu tiên. */
	private List<KnowledgeDoc> fit(List<KnowledgeDoc> docs) {
		Map<String, KnowledgeDoc> unique = new LinkedHashMap<>();
		for (KnowledgeDoc d : docs) {
			unique.putIfAbsent(d.kind() + "#" + d.id(), d);
		}
		List<KnowledgeDoc> result = new ArrayList<>();
		int total = 0;
		for (KnowledgeDoc d : new LinkedHashSet<>(unique.values())) {
			int size = (d.text() == null ? 0 : d.text().length()) + (d.title() == null ? 0 : d.title().length());
			if (total + size > props.maxContextChars() && !result.isEmpty()) {
				break;
			}
			result.add(d);
			total += size;
		}
		return result;
	}

	private static void addAll(List<KnowledgeDoc> target, List<KnowledgeDoc> more) {
		target.addAll(more);
	}

	private static String clean(String value, int max, String emptyMessage) {
		String v = value == null ? "" : value.strip();
		if (v.isEmpty()) {
			throw new ValidationException(emptyMessage);
		}
		return trim(v, max);
	}

	private static String trim(String value, int max) {
		return value.length() > max ? value.substring(0, max) : value;
	}
}
