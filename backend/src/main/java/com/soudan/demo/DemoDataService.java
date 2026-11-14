package com.soudan.demo;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.file.FileStorage;

/**
 * Tạo (và làm mới) dữ liệu demo: một tài khoản Thành viên dùng chung cùng đủ dữ liệu mẫu của mọi tính năng (nhóm, việc ở mọi
 * trạng thái, bình luận, báo cáo, nghỉ phép, thông báo, trò chuyện, lịch) để người xem tự thử nghiệp vụ.
 *
 * <p>Ghi thẳng bằng SQL nên không phụ thuộc module nào. Mỗi lần chạy: xoá dữ liệu demo cũ (nhận biết qua nhóm do người demo
 * tạo, cộng đồng do Manager demo làm chủ, cuộc trò chuyện của người demo) rồi dựng lại theo ngày hôm nay, nên ngày hạn luôn
 * hợp lý. Các tài khoản demo phụ (Leader, Manager, đồng nghiệp) có mật khẩu ngẫu nhiên, không đăng nhập được. Các bản ghi
 * của người dùng thật không bị đụng tới.
 */
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataService {

	private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

	static final String LEADER = "demo.leader@soudan.local";
	static final String MANAGER = "demo.manager@soudan.local";
	static final String LINH = "demo.linh@soudan.local";
	static final String NAM = "demo.nam@soudan.local";
	static final String TAM = "demo.tam@soudan.local";

	private final JdbcTemplate jdbc;
	private final PasswordEncoder encoder;
	private final FileStorage storage;
	private final Clock clock;
	private final ZoneId zone;
	private final DemoProperties properties;

	public DemoDataService(JdbcTemplate jdbc, PasswordEncoder encoder, FileStorage storage, Clock clock, ZoneId zone,
			DemoProperties properties) {
		this.jdbc = jdbc;
		this.encoder = encoder;
		this.storage = storage;
		this.clock = clock;
		this.zone = zone;
		this.properties = properties;
	}

	// ---- người trong dữ liệu mẫu ----
	private long demo;
	private long ha;
	private long minh;
	private long linh;
	private long nam;
	private long tam;
	private Instant now;
	private LocalDate today;

	@Transactional
	public void reseed() {
		now = Instant.now(clock);
		today = LocalDate.now(clock.withZone(zone));
		List<Long> userIds = ensureUsers();
		cleanup(userIds);
		long payTeam = seedTeamsAndTasks();
		seedReports();
		seedChat(payTeam);
		seedCalendar();
		log.info("Đã làm mới dữ liệu demo cho {}", properties.email());
	}

	// ======================= người dùng =======================

	private List<Long> ensureUsers() {
		String publicHash = encoder.encode(properties.password());
		demo = upsertUser(properties.email(), "Nguyễn Minh Anh", "Lập trình viên", "USER", publicHash);
		ha = upsertUser(LEADER, "Trần Thu Hà", "Trưởng nhóm", "USER", randomHash());
		minh = upsertUser(MANAGER, "Lê Quang Minh", "Trưởng phòng", "MANAGER", randomHash());
		linh = upsertUser(LINH, "Phạm Khánh Linh", "Thiết kế sản phẩm", "USER", randomHash());
		nam = upsertUser(NAM, "Hoàng Văn Nam", "Kiểm thử viên", "USER", randomHash());
		tam = upsertUser(TAM, "Vũ Thanh Tâm", "Lập trình viên", "USER", randomHash());
		return List.of(demo, ha, minh, linh, nam, tam);
	}

	private String randomHash() {
		return encoder.encode(UUID.randomUUID().toString());
	}

	/** Có rồi thì đặt lại thông tin và mật khẩu (giữ nguyên id để phiên đăng nhập đang mở không bị mất). */
	private long upsertUser(String email, String name, String position, String role, String hash) {
		List<Long> found = jdbc.queryForList("select id from users where lower(email) = lower(?) and deleted_at is null", Long.class, email);
		if (!found.isEmpty()) {
			jdbc.update("""
					update users set password_hash = ?, full_name = ?, position = ?, system_role = ?, status = 'ACTIVE',
					must_change_password = false, updated_at = now() where id = ?""", hash, name, position, role, found.get(0));
			return found.get(0);
		}
		return id("""
				insert into users (email, password_hash, full_name, position, system_role, status, must_change_password)
				values (?, ?, ?, ?, ?, 'ACTIVE', false) returning id""", email, hash, name, position, role);
	}

	// ======================= dọn dữ liệu demo cũ =======================

	private void cleanup(List<Long> users) {
		List<Long> teams = ids("select id from team where created_by in " + in(users));
		List<Long> communities = ids("select id from community where owner_id in " + in(users));
		List<Long> convs = ids("select id from chat_conversation where user_low_id in " + in(users) + " or user_high_id in "
				+ in(users) + (teams.isEmpty() ? "" : " or team_id in " + in(teams)));
		List<Long> tasks = teams.isEmpty() ? List.of() : ids("select id from task where team_id in " + in(teams));
		List<Long> posts = communities.isEmpty() ? List.of() : ids("select id from report_post where community_id in " + in(communities));
		List<Long> messages = convs.isEmpty() ? List.of() : ids("select id from chat_message where conversation_id in " + in(convs));

		// file đính kèm của dữ liệu demo cũ (xoá trong kho sau khi đã gỡ bản ghi)
		List<String> keys = new ArrayList<>();
		if (!tasks.isEmpty()) {
			keys.addAll(jdbc.queryForList("select storage_key from task_submission where task_id in " + in(tasks), String.class));
		}
		if (!posts.isEmpty()) {
			keys.addAll(jdbc.queryForList("select storage_key from report_attachment where post_id in " + in(posts), String.class));
		}
		if (!messages.isEmpty()) {
			keys.addAll(jdbc.queryForList("select storage_key from chat_attachment where message_id in " + in(messages), String.class));
		}

		jdbc.update("delete from notification where recipient_id in " + in(users) + " or actor_id in " + in(users));
		jdbc.update("delete from ai_usage where user_id in " + in(users));
		jdbc.update("delete from calendar_note where user_id in " + in(users));
		jdbc.update("delete from report_day_off where user_id in " + in(users));

		if (!convs.isEmpty()) {
			jdbc.update("update chat_conversation set last_message_id = null where id in " + in(convs));
			if (!messages.isEmpty()) {
				jdbc.update("update chat_message set reply_to_id = null where id in " + in(messages));
				jdbc.update("delete from chat_reaction where message_id in " + in(messages));
				jdbc.update("delete from chat_attachment where message_id in " + in(messages));
			}
			jdbc.update("delete from chat_read where conversation_id in " + in(convs));
			jdbc.update("delete from chat_message where conversation_id in " + in(convs));
			jdbc.update("delete from chat_conversation where id in " + in(convs));
		}
		if (!communities.isEmpty()) {
			if (!posts.isEmpty()) {
				List<Long> comments = ids("select id from report_comment where post_id in " + in(posts));
				jdbc.update("delete from report_reaction where post_id in " + in(posts));
				if (!comments.isEmpty()) {
					jdbc.update("delete from report_reaction where comment_id in " + in(comments));
					jdbc.update("update report_comment set parent_id = null where id in " + in(comments));
					jdbc.update("delete from report_comment where id in " + in(comments));
				}
				jdbc.update("delete from report_attachment where post_id in " + in(posts));
				jdbc.update("delete from report_post where id in " + in(posts));
			}
			jdbc.update("delete from community_member where community_id in " + in(communities));
			jdbc.update("delete from community where id in " + in(communities));
		}
		if (!teams.isEmpty()) {
			if (!tasks.isEmpty()) {
				List<Long> comments = ids("select id from task_comment where task_id in " + in(tasks));
				if (!comments.isEmpty()) {
					jdbc.update("delete from comment_reaction where comment_id in " + in(comments));
					jdbc.update("update task_comment set parent_id = null where id in " + in(comments));
					jdbc.update("delete from task_comment where id in " + in(comments));
				}
				jdbc.update("delete from task_submission where task_id in " + in(tasks));
				jdbc.update("delete from task_history where task_id in " + in(tasks));
				jdbc.update("delete from task where id in " + in(tasks));
			}
			jdbc.update("delete from team_member where team_id in " + in(teams));
			jdbc.update("delete from team where id in " + in(teams));
		}
		for (String key : keys) {
			try {
				storage.delete(key);
			} catch (IOException e) {
				log.warn("Không xoá được file demo cũ {}", key);
			}
		}
	}

	// ======================= nhóm và việc =======================

	/** Trả về id nhóm "Cổng thanh toán" (phòng chat của nó được dùng ở phần trò chuyện). */
	private long seedTeamsAndTasks() {
		long pay = team("Dự án Cổng thanh toán · Demo", "Tích hợp thanh toán VNPay và QR cho hệ thống bán hàng.", "ACTIVE", ha);
		member(pay, ha, "LEADER");
		member(pay, demo, "MEMBER");
		member(pay, nam, "MEMBER");
		member(pay, tam, "MEMBER");
		member(pay, minh, "OBSERVER");
		long design = team("Thiết kế sản phẩm · Demo", "Thiết kế giao diện và trải nghiệm người dùng.", "ACTIVE", linh);
		member(design, linh, "LEADER");
		member(design, demo, "MEMBER");
		member(design, nam, "MEMBER");
		member(design, minh, "OBSERVER");
		long old = team("Dự án Website nội bộ · Demo (đã lưu trữ)", "Dự án đã bàn giao, nhóm được lưu trữ để xem lại lịch sử.", "ARCHIVED", ha);
		member(old, ha, "LEADER");
		member(old, demo, "MEMBER");
		member(old, nam, "MEMBER");
		this.payTeam = pay;
		this.designTeam = design;
		this.oldTeam = old;

		// --- việc của người demo ở nhóm Cổng thanh toán, đủ mọi trạng thái ---
		long t1 = task(pay, "Tích hợp API cổng thanh toán VNPay",
				"Gọi API tạo giao dịch và xử lý callback IPN. Dùng môi trường sandbox trước khi lên production.", demo, ha, 2,
				"HIGH", "IN_PROGRESS", false, null, 96,
				step(null, "ASSIGNED", ha, 96, null), step("ASSIGNED", "IN_PROGRESS", demo, 70, null));
		long t2 = task(pay, "Viết tài liệu hướng dẫn tích hợp", "Mô tả các bước cấu hình, tham số và ví dụ gọi API cho đội kinh doanh.",
				demo, ha, 6, "MEDIUM", "ASSIGNED", false, null, 2, step(null, "ASSIGNED", ha, 2, null));
		long t3 = task(pay, "Sửa lỗi hiển thị hoá đơn trên mobile", "Hoá đơn bị cắt chữ ở màn hình nhỏ hơn 360px.", demo, ha, 1,
				"MEDIUM", "PENDING_REVIEW", false, null, 120, step(null, "ASSIGNED", ha, 120, null),
				step("ASSIGNED", "IN_PROGRESS", demo, 100, null), step("IN_PROGRESS", "PENDING_REVIEW", demo, 20, null));
		task(pay, "Khảo sát phương thức thanh toán QR", "So sánh VietQR, MoMo và ZaloPay về phí và thời gian đối soát.", demo, ha, -4,
				"MEDIUM", "DONE", false, null, 220, step(null, "ASSIGNED", ha, 220, null),
				step("ASSIGNED", "IN_PROGRESS", demo, 200, null), step("IN_PROGRESS", "PENDING_REVIEW", demo, 100, null),
				step("PENDING_REVIEW", "DONE", ha, 72, null));
		task(pay, "Cập nhật số liệu doanh thu tuần", "Tổng hợp doanh thu theo kênh thanh toán gửi phòng kế toán.", demo, ha, -2, "HIGH",
				"IN_PROGRESS", false, null, 200, step(null, "ASSIGNED", ha, 200, null), step("ASSIGNED", "IN_PROGRESS", demo, 180, null));
		long t6 = task(pay, "Thiết kế lại màn hình xác nhận thanh toán", "Màn hình hiển thị kết quả sau khi khách thanh toán.", demo, ha, 3,
				"MEDIUM", "IN_PROGRESS", false, "Cần thêm trạng thái lỗi và nút thử lại cho trường hợp mất mạng.", 150,
				step(null, "ASSIGNED", ha, 150, null), step("ASSIGNED", "IN_PROGRESS", demo, 140, null),
				step("IN_PROGRESS", "PENDING_REVIEW", demo, 30, null),
				step("PENDING_REVIEW", "IN_PROGRESS", ha, 5, "Cần thêm trạng thái lỗi và nút thử lại cho trường hợp mất mạng."));
		task(pay, "Nộp báo cáo kiểm thử hồi quy", "Việc này yêu cầu nộp file báo cáo (PDF hoặc Word) khi hoàn thành.", demo, ha, 4, "LOW",
				"ASSIGNED", true, null, 6, step(null, "ASSIGNED", ha, 6, null));
		// --- việc của đồng nghiệp trong cùng nhóm (người demo xem được) ---
		task(pay, "Chuẩn bị dữ liệu kiểm thử thanh toán", "Tạo bộ dữ liệu giao dịch thành công, thất bại và hoàn tiền.", nam, ha, 3,
				"MEDIUM", "IN_PROGRESS", false, null, 80, step(null, "ASSIGNED", ha, 80, null), step("ASSIGNED", "IN_PROGRESS", nam, 60, null));
		task(pay, "Cấu hình môi trường staging", "Dựng staging có chứng chỉ HTTPS để nhận callback.", tam, ha, -3, "HIGH", "DONE", false, null, 190,
				step(null, "ASSIGNED", ha, 190, null), step("ASSIGNED", "IN_PROGRESS", tam, 170, null),
				step("IN_PROGRESS", "PENDING_REVIEW", tam, 90, null), step("PENDING_REVIEW", "DONE", ha, 80, null));
		task(pay, "Viết test tự động cho callback IPN", "Phủ các trường hợp chữ ký sai, trùng giao dịch, quá hạn.", nam, ha, 7, "MEDIUM",
				"ASSIGNED", false, null, 10, step(null, "ASSIGNED", ha, 10, null));
		// --- nhóm Thiết kế ---
		task(design, "Chọn bảng màu cho dashboard", "Đề xuất 3 phương án màu, thử độ tương phản cho người khiếm thị màu.", demo, linh, 5,
				"MEDIUM", "IN_PROGRESS", false, null, 50, step(null, "ASSIGNED", linh, 50, null), step("ASSIGNED", "IN_PROGRESS", demo, 30, null));
		task(design, "Review prototype Figma với khách", "Họp review và ghi nhận phản hồi.", demo, linh, -1, "LOW", "DONE", false, null, 170,
				step(null, "ASSIGNED", linh, 170, null), step("ASSIGNED", "IN_PROGRESS", demo, 150, null),
				step("IN_PROGRESS", "PENDING_REVIEW", demo, 60, null), step("PENDING_REVIEW", "DONE", linh, 48, null));
		task(design, "Làm bộ icon trạng thái", "Icon cho các trạng thái việc: đã giao, đang làm, chờ duyệt, hoàn thành.", nam, linh, 8, "LOW",
				"ASSIGNED", false, null, 12, step(null, "ASSIGNED", linh, 12, null));

		byte[] pdf = pdfBytes();
		attachSubmission(t3, "bao-cao-sua-loi-hoa-don.pdf", "application/pdf", pdf, demo, 20);

		// --- bình luận, trả lời, biểu cảm trên việc ---
		long c1 = taskComment(t1, ha, null, "Em nhớ dùng môi trường sandbox của VNPay trước nhé, đừng gọi thẳng production.", 30);
		long c1r = taskComment(t1, demo, c1, "Dạ chị, em đang dùng sandbox. Em sẽ xong phần callback trong hôm nay ạ.", 28);
		long c2 = taskComment(t1, nam, null, "Anh cần chữ ký HMAC mẫu để viết test, em gửi giúp anh nhé.", 10);
		long c2r = taskComment(t1, demo, c2, "Em đã đưa lên thư mục chung rồi anh.", 9);
		reactTask(c1, demo, "👍");
		reactTask(c1, tam, "🙌");
		reactTask(c1r, ha, "👍");
		reactTask(c2r, nam, "❤️");
		long c3 = taskComment(t3, ha, null, "Em đính kèm thêm ảnh trước/sau khi sửa để chị duyệt nhanh hơn nhé.", 22);
		long c3r = taskComment(t3, demo, c3, "Em đã bổ sung trong file báo cáo ạ.", 21);
		reactTask(c3r, ha, "👍");
		long c4 = taskComment(t6, ha, null, "Chị trả lại để bổ sung trạng thái lỗi nhé, còn lại ổn.", 5);
		long c4r = taskComment(t6, demo, c4, "Dạ em sẽ cập nhật trong chiều nay.", 4);
		reactTask(c4r, ha, "👍");

		// --- thông báo của người demo ---
		notify("TASK_ASSIGNED", ha, t2, null, null, null, "Viết tài liệu hướng dẫn tích hợp", null, 120, false);
		notify("TASK_REVISION", ha, t6, null, null, null, "Thiết kế lại màn hình xác nhận thanh toán",
				"Cần thêm trạng thái lỗi và nút thử lại cho trường hợp mất mạng.", 300, false);
		notify("TASK_COMMENT", nam, t1, null, null, null, "Tích hợp API cổng thanh toán VNPay",
				"Anh cần chữ ký HMAC mẫu để viết test, em gửi giúp anh nhé.", 600, false);
		notify("TASK_REPLY", ha, t1, null, null, null, "Tích hợp API cổng thanh toán VNPay", "Em nhớ dùng môi trường sandbox của VNPay trước nhé.", 1700, true);
		notify("TASK_REACTION", tam, t1, null, null, null, "Tích hợp API cổng thanh toán VNPay", "🙌", 1650, true);
		notify("TASK_DUE_SOON", null, t1, null, null, null, "Tích hợp API cổng thanh toán VNPay", today.plusDays(2).toString(), 60, false);
		this.attentionTask = t1;
		return pay;
	}

	private long payTeam;
	private long designTeam;
	private long oldTeam;
	private long attentionTask;

	// ======================= báo cáo =======================

	private void seedReports() {
		long c1 = community("Báo cáo hằng ngày · Phòng phát triển · Demo", "Mỗi người một trang báo cáo riêng; mọi thành viên xem được báo cáo của nhau.");
		communityMember(c1, demo);
		communityMember(c1, ha);
		communityMember(c1, nam);
		communityMember(c1, tam);
		long c2 = community("Bản tin tuần · Thiết kế · Demo", "Chia sẻ tiến độ và vướng mắc thiết kế trong tuần.");
		communityMember(c2, demo);
		communityMember(c2, linh);

		List<LocalDate> days = recentWorkdays(4);
		long demoPostForNotes = 0;
		long demoFirstPost = 0;
		for (int i = 0; i < days.size(); i++) {
			LocalDate d = days.get(i);
			boolean isToday = d.equals(today);
			// Người demo chưa viết báo cáo hôm nay để tự thử "Viết báo cáo"
			if (!(isToday && i == 0)) {
				long p = post(c1, demo, d, "Báo cáo ngày " + vn(d),
						body(DONE[i % DONE.length], PLAN[i % PLAN.length], i == 1 ? "Chưa có tài khoản sandbox đủ hạn mức để chạy thử tải." : null));
				if (i == 1) {
					demoPostForNotes = p;
					attachReport(p, "anh-man-hinh-thanh-toan.png", "image/png", png(new Color(0x4f52b2), new Color(0x8764b8)), demo);
				}
				if (demoFirstPost == 0) {
					demoFirstPost = p;
				}
			}
			long hp = post(c1, ha, d, "Báo cáo ngày " + vn(d), body(HA_DONE[i % HA_DONE.length], "Họp với QA về luồng nộp file, chuẩn bị demo.", null));
			if (!(isToday && i == 0)) {
				post(c1, nam, d, "Báo cáo ngày " + vn(d), body(NAM_DONE[i % NAM_DONE.length], "Viết thêm test cho trường hợp hoàn tiền.", i == 2 ? "Cần Manager duyệt thêm 2 tài khoản thử nghiệm." : null));
			}
			if (!(isToday && i == 0)) {
				post(c1, tam, d, "Báo cáo ngày " + vn(d), body("Dựng staging, cấu hình chứng chỉ HTTPS và kiểm tra callback IPN.", "Theo dõi log callback.", null));
			}
			if (i == 0) {
				postReaction(hp, demo, "👍");
			}
		}
		if (demoPostForNotes != 0) {
			long mc = reportComment(demoPostForNotes, minh, null, "Tiến độ tốt, anh đã xem phần callback. Nhớ ghi chú lại các trường hợp lỗi nhé.", 20);
			long mr = reportComment(demoPostForNotes, demo, mc, "Dạ anh, em sẽ bổ sung vào tài liệu hướng dẫn ạ.", 19);
			long hc = reportComment(demoPostForNotes, ha, null, "Phần ảnh minh hoạ rất rõ, chị đã chuyển cho nhóm kiểm thử.", 18);
			postReaction(demoPostForNotes, minh, "❤️");
			postReaction(demoPostForNotes, ha, "👍");
			postReaction(demoPostForNotes, nam, "🎉");
			commentReaction(mc, demo, "👍");
			commentReaction(mr, minh, "👍");
			commentReaction(hc, demo, "❤️");
			notify("REPORT_COMMENT", minh, null, c1, demoPostForNotes, demo, "Báo cáo của bạn", "Tiến độ tốt, anh đã xem phần callback.", 1200, false);
			notify("REPORT_REPLY", ha, null, c1, demoPostForNotes, demo, "Báo cáo của bạn", "Phần ảnh minh hoạ rất rõ, chị đã chuyển cho nhóm kiểm thử.", 1100, true);
			notify("REPORT_REACTION", ha, null, c1, demoPostForNotes, demo, "Báo cáo của bạn", "👍", 1000, true);
		}
		// Nhắc nộp báo cáo (hệ thống), chỉ ngày làm việc
		if (isWorkday(today)) {
			notify("REPORT_REMINDER", null, null, c1, null, null, "Báo cáo hằng ngày · Phòng phát triển · Demo", null, 30, false);
			jdbc.update("insert into report_day_off (user_id, day, reason) values (?, ?, ?)", tam, Date.valueOf(today), "Khám sức khoẻ định kỳ");
		}
		jdbc.update("insert into report_day_off (user_id, day, reason) values (?, ?, ?)", demo, Date.valueOf(today.plusDays(5)), "Việc gia đình");
		// Bản tin thiết kế của Linh
		LocalDate d = days.get(Math.min(1, days.size() - 1));
		post(c2, linh, d, "Bản tin tuần thiết kế", body("Hoàn thiện bộ icon trạng thái và chốt bảng màu cho dashboard.", "Review với khách hàng vào thứ Sáu.", null));
	}

	private static final String[] DONE = {
			"Hoàn thiện tích hợp API tạo giao dịch VNPay và xử lý callback IPN.",
			"Viết xong phần gọi API tạo giao dịch trên sandbox, đã chạy thử luồng thành công.",
			"Sửa lỗi hiển thị hoá đơn trên mobile và cập nhật bộ test giao diện.",
			"Khảo sát các phương thức thanh toán QR và tổng hợp bảng so sánh phí." };
	private static final String[] PLAN = {
			"Viết tài liệu hướng dẫn tích hợp và chuẩn bị demo thứ Sáu.",
			"Xử lý trường hợp giao dịch trùng và hoàn tiền.",
			"Làm phần xác nhận thanh toán thất bại, thêm nút thử lại.",
			"Tích hợp thêm VietQR nếu còn thời gian." };
	private static final String[] HA_DONE = {
			"Review code PR #42 và họp với bên QA về luồng nộp file.",
			"Duyệt các việc đang chờ và lên kế hoạch cho sprint tiếp theo.",
			"Làm việc với khách hàng về yêu cầu đối soát giao dịch.",
			"Chốt phạm vi demo và phân công lại các việc còn tồn." };
	private static final String[] NAM_DONE = {
			"Chuẩn bị bộ dữ liệu kiểm thử giao dịch thành công, thất bại và hoàn tiền.",
			"Chạy test hồi quy cho module thanh toán, phát hiện 2 lỗi nhỏ đã ghi lại.",
			"Viết kịch bản test tự động cho callback IPN.",
			"Kiểm thử hiệu năng với 200 giao dịch đồng thời." };

	// ======================= trò chuyện =======================

	private void seedChat(long pay) {
		// --- nhắn riêng với trưởng nhóm (đã chấp nhận) ---
		long dm = directConversation(demo, ha, "ACCEPTED", ha, 1600);
		long m1 = message(dm, ha, 1500, "Chào em, chị thấy task tích hợp VNPay sắp đến hạn rồi, em cần hỗ trợ gì không?", null);
		message(dm, demo, 1480, "Dạ chị, em đang làm dở phần callback, chiều nay em xong ạ.", null);
		long m3 = message(dm, ha, 1470, "Ok em, nhớ cập nhật trạng thái task nhé.", null);
		long m4 = message(dm, demo, 1440, "Chị xem giúp em sơ đồ luồng thanh toán này với.", null);
		attachChat(m4, "so-do-luong-thanh-toan.png", "image/png", png(new Color(0x0078d4), new Color(0x4f52b2)));
		long m5 = message(dm, ha, 1430, "Sơ đồ rõ ràng, chị thấy hợp lý 👍", m4);
		long m6 = message(dm, demo, 120, "Chị ơi em vừa đẩy xong PR cho phần callback, chị review giúp em nhé.", null);
		jdbc.update("update chat_message set edited_at = ? where id = ?", ts(minutesAgo(110)), m6);
		long gone = message(dm, ha, 100, "(gửi nhầm)", null);
		jdbc.update("update chat_message set deleted_at = ? where id = ?", ts(minutesAgo(99)), gone);
		long m7 = message(dm, ha, 90, "Được em, chị xem ngay.", null);
		message(dm, ha, 40, "Chị đã comment trong PR, em xem giúp chị nhé.", null);
		message(dm, ha, 12, "Em ơi 3h chiều họp nhanh 10 phút về demo nhé.", null);
		chatReaction(m3, demo, "👍");
		chatReaction(m5, demo, "❤️");
		chatReaction(m1, demo, "🙌");
		finishConversation(dm, m7);
		// --- lời mời nhắn tin: Linh mời người demo (chờ chấp nhận), người demo mời Nam (đang chờ) ---
		long invite = directConversation(linh, demo, "PENDING", linh, 200);
		finishConversation(invite, 0);
		directConversation(demo, nam, "PENDING", demo, 400);
		notify("CHAT_REQUEST", linh, null, null, null, null, null, null, 200, false, invite);
		notify("CHAT_REQUEST_ACCEPTED", ha, null, null, null, null, null, null, 1590, true, dm);

		// --- phòng chat nhóm Cổng thanh toán ---
		long room = teamConversation(pay, 3000);
		message(room, ha, 2900, "Chào cả nhóm, sprint này mình tập trung hoàn thiện module thanh toán nhé. Hạn demo là thứ Sáu.", null);
		message(room, nam, 2880, "Dạ chị, em lo phần dữ liệu kiểm thử.", null);
		message(room, tam, 2870, "Em đã dựng xong staging, mọi người dùng thử giúp em.", null);
		message(room, demo, 2800, "Em nhận phần API callback VNPay ạ.", null);
		message(room, minh, 1500, "Anh theo dõi tiến độ, cần hỗ trợ gì cứ báo anh nhé.", null);
		message(room, nam, 600, "Mọi người ơi, sandbox VNPay đang chậm, em đã báo bên VNPay.", null);
		long tamErr = message(room, tam, 300, "Callback IPN lúc nãy trả lỗi 500, em đang kiểm tra.", null);
		message(room, ha, 200, "Em gửi log lên đây để cả nhóm cùng xem nhé.", tamErr);
		long log = message(room, tam, 190, "Log lỗi đây ạ.", null);
		attachChat(log, "log-loi-callback.png", "image/png", png(new Color(0xc4314b), new Color(0x8764b8)));
		long mine = message(room, demo, 60, "Em nghĩ do chữ ký HMAC bị sai múi giờ, em thử lại ngay.", null);
		message(room, ha, 30, "Ok em, báo chị kết quả nhé.", null);
		long pass = message(room, nam, 15, "Em chạy lại test rồi, đã pass 🎉", null);
		message(room, tam, 8, "Tuyệt vời! Mình chốt demo thứ Sáu nhé.", null);
		chatReaction(pass, demo, "🎉");
		chatReaction(pass, ha, "👏");
		chatReaction(mine, nam, "👍");
		finishConversation(room, mine);
		// --- phòng chat nhóm Thiết kế ---
		long design = teamConversation(designTeam, 4100);
		message(design, linh, 4000, "Mọi người gửi mình bảng màu mới trước thứ Tư nhé.", null);
		message(design, demo, 3900, "Em đã chọn xong 3 phương án, em gửi file Figma sau ạ.", null);
		long last = message(design, nam, 3800, "Em thích phương án 2 hơn 👍", null);
		finishConversation(design, last);
		// --- phòng chat nhóm đã lưu trữ (chỉ xem) ---
		long old = teamConversation(oldTeam, 29000);
		message(old, ha, 28800, "Dự án đã bàn giao xong, mình lưu trữ nhóm nhé.", null);
		long end = message(old, demo, 28700, "Cảm ơn chị và cả nhóm ạ!", null);
		finishConversation(old, end);
	}

	// ======================= lịch =======================

	private void seedCalendar() {
		calendar(-1, "Nộp báo cáo quý cho phòng kế toán", "Gửi bản tổng hợp trước 17h.", "RED");
		calendar(1, "Họp sprint planning 09:30", "Phòng họp tầng 3, mang theo danh sách việc tồn.", "BLUE");
		calendar(2, "Demo sản phẩm cho khách hàng", "Chuẩn bị kịch bản demo luồng thanh toán.", "ORANGE");
		calendar(4, "Hạn nộp tài liệu tích hợp", null, "RED");
		calendar(7, "Sinh nhật Linh 🎂", "Góp quà cùng cả nhóm.", "PURPLE");
		calendar(9, "Review code cùng chị Hà", null, "GREEN");
		calendar(14, "Team building cuối tháng", "Đăng ký tham gia trước thứ Sáu tuần sau.", "INDIGO");
	}

	// ======================= hàm hỗ trợ ghi dữ liệu =======================

	private long team(String name, String description, String status, long createdBy) {
		return id("insert into team (name, description, status, created_by, updated_by) values (?, ?, ?, ?, ?) returning id", name, description, status,
				createdBy, createdBy);
	}

	private void member(long team, long user, String role) {
		jdbc.update("insert into team_member (team_id, user_id, team_role, joined_at) values (?, ?, ?, ?)", team, user, role, ts(daysAgo(30)));
	}

	private record Step(String from, String to, long by, int hoursAgo, String note) {
	}

	private static Step step(String from, String to, long by, int hoursAgo, String note) {
		return new Step(from, to, by, hoursAgo, note);
	}

	private long task(long team, String title, String description, long assignee, long assigner, int dueOffset, String priority, String status,
			boolean requireFile, String revision, int createdHoursAgo, Step... steps) {
		long created = ts(hoursAgo(createdHoursAgo)).getTime();
		long id = id("""
				insert into task (team_id, title, description, assignee_id, assigned_by, due_date, priority, status, require_file, revision_reason,
				                  created_at, created_by, updated_at, updated_by)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id""", team, title, description, assignee, assigner,
				Date.valueOf(today.plusDays(dueOffset)), priority, status, requireFile, revision, new Timestamp(created), assigner,
				ts(hoursAgo(steps.length == 0 ? createdHoursAgo : steps[steps.length - 1].hoursAgo)), assigner);
		for (Step s : steps) {
			jdbc.update("insert into task_history (task_id, from_status, to_status, changed_by, changed_at, note) values (?, ?, ?, ?, ?, ?)", id, s.from,
					s.to, s.by, ts(hoursAgo(s.hoursAgo)), s.note);
		}
		return id;
	}

	private void attachSubmission(long task, String name, String type, byte[] content, long by, int hoursAgo) {
		jdbc.update("insert into task_submission (task_id, file_name, content_type, size_bytes, storage_key, uploaded_by, uploaded_at) values (?, ?, ?, ?, ?, ?, ?)",
				task, name, type, content.length, store(content), by, ts(hoursAgo(hoursAgo)));
	}

	private long taskComment(long task, long author, Long parent, String body, int hoursAgo) {
		Timestamp at = ts(hoursAgo(hoursAgo));
		return id("""
				insert into task_comment (task_id, author_id, body, created_at, created_by, updated_at, updated_by, parent_id)
				values (?, ?, ?, ?, ?, ?, ?, ?) returning id""", task, author, body, at, author, at, author, parent);
	}

	private void reactTask(long comment, long user, String emoji) {
		jdbc.update("insert into comment_reaction (comment_id, user_id, emoji, created_at) values (?, ?, ?, ?)", comment, user, emoji, ts(minutesAgo(30)));
	}

	private long community(String name, String description) {
		return id("insert into community (name, description, owner_id, created_by, updated_by) values (?, ?, ?, ?, ?) returning id", name, description, minh,
				minh, minh);
	}

	private void communityMember(long community, long user) {
		jdbc.update("insert into community_member (community_id, user_id, added_at) values (?, ?, ?)", community, user, ts(daysAgo(20)));
	}

	private long post(long community, long author, LocalDate day, String title, String body) {
		Instant at = day.equals(today) ? minutesAgoOrEarlier(day, 17, 30) : day.atTime(17, 30).atZone(zone).toInstant();
		return id("""
				insert into report_post (community_id, author_id, report_date, title, body, created_at, created_by, updated_at, updated_by)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id""", community, author, Date.valueOf(day), title, body, ts(at), author, ts(at), author);
	}

	private void attachReport(long post, String name, String type, byte[] content, long by) {
		jdbc.update("insert into report_attachment (post_id, file_name, content_type, size_bytes, storage_key, uploaded_by, uploaded_at) values (?, ?, ?, ?, ?, ?, ?)",
				post, name, type, content.length, store(content), by, ts(daysAgo(1)));
	}

	private long reportComment(long post, long author, Long parent, String body, int hoursAgo) {
		Timestamp at = ts(hoursAgo(hoursAgo));
		return id("""
				insert into report_comment (post_id, author_id, parent_id, body, created_at, created_by, updated_at, updated_by)
				values (?, ?, ?, ?, ?, ?, ?, ?) returning id""", post, author, parent, body, at, author, at, author);
	}

	private void postReaction(long post, long user, String emoji) {
		jdbc.update("insert into report_reaction (post_id, comment_id, user_id, emoji, created_at) values (?, null, ?, ?, ?)", post, user, emoji, ts(minutesAgo(40)));
	}

	private void commentReaction(long comment, long user, String emoji) {
		jdbc.update("insert into report_reaction (post_id, comment_id, user_id, emoji, created_at) values (null, ?, ?, ?, ?)", comment, user, emoji, ts(minutesAgo(35)));
	}

	private void notify(String type, Long actor, Long task, Long community, Long post, Long postAuthor, String subject, String snippet, int minutesAgo,
			boolean read) {
		notify(type, actor, task, community, post, postAuthor, subject, snippet, minutesAgo, read, null);
	}

	private void notify(String type, Long actor, Long task, Long community, Long post, Long postAuthor, String subject, String snippet, int minutesAgo,
			boolean read, Long conversation) {
		Timestamp at = ts(minutesAgo(minutesAgo));
		jdbc.update("""
				insert into notification (recipient_id, type, actor_id, task_id, community_id, post_id, post_author_id, subject, snippet, created_at, read_at,
				                          conversation_id)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", demo, type, actor, task, community, post, postAuthor, subject, snippet, at,
				read ? at : null, conversation);
	}

	private long directConversation(long a, long b, String status, long requester, int minutesAgo) {
		long low = Math.min(a, b);
		long high = Math.max(a, b);
		return id("""
				insert into chat_conversation (type, user_low_id, user_high_id, status, requested_by, responded_at, created_at)
				values ('DIRECT', ?, ?, ?, ?, ?, ?) returning id""", low, high, status, requester,
				"ACCEPTED".equals(status) ? ts(minutesAgo(minutesAgo - 5)) : null, ts(minutesAgo(minutesAgo)));
	}

	private long teamConversation(long team, int minutesAgo) {
		return id("insert into chat_conversation (type, team_id, status, created_at) values ('TEAM', ?, 'ACCEPTED', ?) returning id", team, ts(minutesAgo(minutesAgo)));
	}

	private long message(long conversation, long author, int minutesAgo, String body, Long replyTo) {
		return id("insert into chat_message (conversation_id, author_id, body, reply_to_id, created_at) values (?, ?, ?, ?, ?) returning id", conversation, author,
				body, replyTo, ts(minutesAgo(minutesAgo)));
	}

	private void attachChat(long message, String name, String type, byte[] content) {
		jdbc.update("insert into chat_attachment (message_id, file_name, content_type, size_bytes, storage_key) values (?, ?, ?, ?, ?)", message, name, type,
				content.length, store(content));
	}

	private void chatReaction(long message, long user, String emoji) {
		jdbc.update("insert into chat_reaction (message_id, user_id, emoji, created_at) values (?, ?, ?, ?)", message, user, emoji, ts(minutesAgo(10)));
	}

	/** Ghi tin cuối của cuộc trò chuyện và mốc đã đọc của người demo (các tin sau mốc là chưa đọc). */
	private void finishConversation(long conversation, long demoLastReadMessage) {
		List<Long> last = jdbc.queryForList("select id from chat_message where conversation_id = ? order by id desc limit 1", Long.class, conversation);
		if (!last.isEmpty()) {
			jdbc.update("update chat_conversation set last_message_id = ?, last_message_at = (select created_at from chat_message where id = ?) where id = ?",
					last.get(0), last.get(0), conversation);
		}
		jdbc.update("insert into chat_read (conversation_id, user_id, last_read_message_id) values (?, ?, ?)", conversation, demo, demoLastReadMessage);
	}

	private void calendar(int offset, String title, String content, String color) {
		jdbc.update("insert into calendar_note (user_id, note_date, title, content, color) values (?, ?, ?, ?, ?)", demo, Date.valueOf(today.plusDays(offset)), title,
				content, color);
	}

	// ======================= tiện ích =======================

	private long id(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	private List<Long> ids(String sql) {
		return jdbc.queryForList(sql, Long.class);
	}

	/** "(1,2,3)" từ danh sách số (không phải chuỗi người dùng nhập nên an toàn); danh sách rỗng thành "(-1)". */
	private static String in(List<Long> ids) {
		return ids.isEmpty() ? "(-1)" : "(" + String.join(",", ids.stream().map(String::valueOf).toList()) + ")";
	}

	private Timestamp ts(Instant instant) {
		return Timestamp.from(instant);
	}

	private Instant minutesAgo(int minutes) {
		return now.minus(minutes, ChronoUnit.MINUTES);
	}

	private Instant hoursAgo(int hours) {
		return now.minus(hours, ChronoUnit.HOURS);
	}

	private Instant daysAgo(int days) {
		return now.minus(days, ChronoUnit.DAYS);
	}

	/** 17:30 của ngày đó, nhưng không muộn hơn 30 phút trước bây giờ (báo cáo hôm nay không được ở tương lai). */
	private Instant minutesAgoOrEarlier(LocalDate day, int hour, int minute) {
		Instant planned = day.atTime(hour, minute).atZone(zone).toInstant();
		Instant limit = minutesAgo(30);
		return planned.isAfter(limit) ? limit : planned;
	}

	private static String vn(LocalDate d) {
		return "%02d/%02d/%d".formatted(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
	}

	private static boolean isWorkday(LocalDate d) {
		return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
	}

	/** Các ngày làm việc gần nhất, mới nhất trước (bắt đầu từ hôm nay nếu là ngày làm việc). */
	private List<LocalDate> recentWorkdays(int count) {
		List<LocalDate> result = new ArrayList<>();
		LocalDate d = today;
		while (result.size() < count) {
			if (isWorkday(d)) {
				result.add(d);
			}
			d = d.minusDays(1);
		}
		return result;
	}

	private static String body(String done, String plan, String issue) {
		StringBuilder html = new StringBuilder("<h2>Việc đã làm</h2><ul><li>").append(done).append("</li></ul>");
		html.append("<h2>Kế hoạch</h2><p>").append(plan).append("</p>");
		if (issue != null) {
			html.append("<h2>Vướng mắc</h2><p>").append(issue).append("</p>");
		}
		return html.toString();
	}

	private String store(byte[] content) {
		try {
			return storage.save(new ByteArrayInputStream(content));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Ảnh minh hoạ nền chuyển màu (không vẽ chữ nên không phụ thuộc phông của máy chủ). */
	private static byte[] png(Color from, Color to) {
		BufferedImage image = new BufferedImage(480, 270, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, from, 480, 270, to));
		g.fillRect(0, 0, 480, 270);
		g.setColor(new Color(255, 255, 255, 70));
		g.fillRoundRect(40, 50, 400, 40, 14, 14);
		g.fillRoundRect(40, 110, 260, 28, 14, 14);
		g.fillRoundRect(40, 160, 330, 28, 14, 14);
		g.fillRoundRect(40, 210, 180, 28, 14, 14);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	/** Một trang PDF tối giản để thử tải file nộp. */
	private static byte[] pdfBytes() {
		String pdf = """
				%PDF-1.4
				1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
				2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
				3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 144]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj
				4 0 obj<</Length 44>>stream
				BT /F1 18 Tf 20 70 Td (File demo E-Soudan) Tj ET
				endstream endobj
				5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj
				trailer<</Root 1 0 R/Size 6>>
				%%EOF
				""";
		return pdf.getBytes(StandardCharsets.ISO_8859_1);
	}

	// Sắp xếp danh sách cho phép test đọc lại id người demo (không dùng ở nghiệp vụ)
	List<Long> demoUserIds() {
		return Collections.unmodifiableList(List.of(demo, ha, minh, linh, nam, tam));
	}
}
