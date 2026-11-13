package com.soudan.report.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;
import com.soudan.report.service.ReportService;
import com.soudan.report.web.ReportDtos.CommentRequest;
import com.soudan.report.web.ReportDtos.CommentResponse;
import com.soudan.report.web.ReportDtos.PostResponse;
import com.soudan.report.web.ReportDtos.ReactionRequest;

import jakarta.validation.Valid;

/** Một báo cáo, biểu cảm và bình luận của nó. Quyền kiểm tra trong service. */
@RestController
@RequestMapping("/api/v1/report-posts/{postId}")
public class ReportPostController {

	private final ReportService reports;

	public ReportPostController(ReportService reports) {
		this.reports = reports;
	}

	@GetMapping
	public ApiResponse<PostResponse> get(@PathVariable Long postId) {
		return ApiResponse.ok(PostResponse.of(reports.getPost(postId)));
	}

	@GetMapping("/attachments/{attachmentId}/download")
	public ResponseEntity<InputStreamResource> download(@PathVariable Long postId, @PathVariable Long attachmentId) {
		var file = reports.download(postId, attachmentId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.sizeBytes())
				.body(new InputStreamResource(file.content()));
	}

	@PutMapping("/reactions")
	public ApiResponse<PostResponse> react(@PathVariable Long postId, @Valid @RequestBody ReactionRequest request) {
		return ApiResponse.ok(PostResponse.of(reports.reactToPost(postId, request.emoji())));
	}

	@DeleteMapping("/reactions")
	public ApiResponse<PostResponse> unreact(@PathVariable Long postId, @RequestParam String emoji) {
		return ApiResponse.ok(PostResponse.of(reports.unreactPost(postId, emoji)));
	}

	@GetMapping("/comments")
	public ApiResponse<List<CommentResponse>> comments(@PathVariable Long postId) {
		return ApiResponse.ok(reports.listComments(postId).stream().map(CommentResponse::of).toList());
	}

	@PostMapping("/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CommentResponse> addComment(@PathVariable Long postId,
			@Valid @RequestBody CommentRequest request) {
		return ApiResponse.ok(CommentResponse.of(reports.addComment(postId, request.body(), request.replyToId())));
	}

	@PutMapping("/comments/{commentId}")
	public ApiResponse<CommentResponse> editComment(@PathVariable Long postId, @PathVariable Long commentId,
			@Valid @RequestBody CommentRequest request) {
		return ApiResponse.ok(CommentResponse.of(reports.editComment(postId, commentId, request.body())));
	}

	@DeleteMapping("/comments/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteComment(@PathVariable Long postId, @PathVariable Long commentId) {
		reports.deleteComment(postId, commentId);
	}

	@PutMapping("/comments/{commentId}/reactions")
	public ApiResponse<CommentResponse> reactToComment(@PathVariable Long postId, @PathVariable Long commentId,
			@Valid @RequestBody ReactionRequest request) {
		return ApiResponse.ok(CommentResponse.of(reports.reactToComment(postId, commentId, request.emoji())));
	}

	@DeleteMapping("/comments/{commentId}/reactions")
	public ApiResponse<CommentResponse> unreactComment(@PathVariable Long postId, @PathVariable Long commentId,
			@RequestParam String emoji) {
		return ApiResponse.ok(CommentResponse.of(reports.unreactComment(postId, commentId, emoji)));
	}
}
