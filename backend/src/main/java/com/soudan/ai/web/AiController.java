package com.soudan.ai.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.ai.service.AiService;
import com.soudan.ai.service.AiService.Answer;
import com.soudan.ai.service.AiService.HistoryTurn;
import com.soudan.ai.service.AiService.RewriteMode;
import com.soudan.common.api.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Trợ lý AI cho người đang đăng nhập (mọi vai trò trừ Admin). Mọi dữ liệu AI đọc đều đã lọc theo quyền của người hỏi. */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

	private static final String LANG = "vi|ja|en";

	public record HistoryItem(@Pattern(regexp = "user|assistant") String role, @Size(max = 4000) String text) {
	}

	public record AskRequest(@Size(max = 1000) String question, @Size(max = 12) List<@Valid HistoryItem> history,
			@Pattern(regexp = LANG) String lang) {
	}

	public record CommunitySummaryRequest(@NotNull Long communityId, LocalDate from, LocalDate to,
			@Pattern(regexp = LANG) String lang) {
	}

	public record ChatSummaryRequest(@NotNull Long conversationId, Integer hours, @Pattern(regexp = LANG) String lang) {
	}

	public record LangRequest(@Pattern(regexp = LANG) String lang) {
	}

	public record RewriteRequest(@Size(max = 4000) String text, @NotNull RewriteMode mode,
			@Pattern(regexp = LANG) String lang) {
	}

	public record PlanRequest(Integer days, @Pattern(regexp = LANG) String lang) {
	}

	public record SourceResponse(int n, String kind, String title, String path, java.time.Instant at) {
	}

	public record AnswerResponse(String text, List<SourceResponse> sources) {
		static AnswerResponse of(Answer a) {
			return new AnswerResponse(a.text(),
					a.sources().stream().map(s -> new SourceResponse(s.n(), s.kind(), s.title(), s.path(), s.at())).toList());
		}
	}

	private final AiService service;

	public AiController(AiService service) {
		this.service = service;
	}

	/** Trợ lý đã cấu hình chưa (để giao diện báo cho người dùng thay vì để họ thử rồi gặp lỗi). */
	@GetMapping("/status")
	public ApiResponse<Map<String, Boolean>> status() {
		return ApiResponse.ok(Map.of("enabled", service.enabled()));
	}

	@PostMapping("/ask")
	public ApiResponse<AnswerResponse> ask(@Valid @RequestBody AskRequest request) {
		List<HistoryTurn> history = request.history() == null ? List.of()
				: request.history().stream().map(h -> new HistoryTurn(h.role(), h.text())).toList();
		return ApiResponse.ok(AnswerResponse.of(service.ask(request.question(), history, request.lang())));
	}

	@PostMapping("/summaries/community")
	public ApiResponse<AnswerResponse> summarizeCommunity(@Valid @RequestBody CommunitySummaryRequest request) {
		return ApiResponse.ok(AnswerResponse.of(
				service.summarizeCommunity(request.communityId(), request.from(), request.to(), request.lang())));
	}

	@PostMapping("/summaries/chat")
	public ApiResponse<AnswerResponse> summarizeChat(@Valid @RequestBody ChatSummaryRequest request) {
		return ApiResponse.ok(AnswerResponse.of(service.summarizeChat(request.conversationId(),
				request.hours() == null ? 24 : request.hours(), request.lang())));
	}

	@PostMapping("/assist/draft-report")
	public ApiResponse<AnswerResponse> draftReport(@Valid @RequestBody LangRequest request) {
		return ApiResponse.ok(AnswerResponse.of(service.draftReport(request.lang())));
	}

	@PostMapping("/assist/rewrite")
	public ApiResponse<AnswerResponse> rewrite(@Valid @RequestBody RewriteRequest request) {
		return ApiResponse.ok(AnswerResponse.of(service.rewrite(request.text(), request.mode(), request.lang())));
	}

	@PostMapping("/plan")
	public ApiResponse<AnswerResponse> plan(@Valid @RequestBody PlanRequest request) {
		return ApiResponse.ok(AnswerResponse.of(service.plan(request.days() == null ? 7 : request.days(), request.lang())));
	}
}
