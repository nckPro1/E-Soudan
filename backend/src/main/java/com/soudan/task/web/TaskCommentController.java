package com.soudan.task.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;
import com.soudan.task.mapper.TaskMapper;
import com.soudan.task.service.TaskCommentService;

import jakarta.validation.Valid;

/** Bình luận trên task. Quyền kiểm tra trong service (cần biết nhóm của task). */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/comments")
public class TaskCommentController {

	private final TaskCommentService service;
	private final TaskMapper mapper;

	public TaskCommentController(TaskCommentService service, TaskMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@GetMapping
	public ApiResponse<List<CommentResponse>> list(@PathVariable Long taskId) {
		return ApiResponse.ok(mapper.toComments(service.list(taskId)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CommentResponse> add(@PathVariable Long taskId, @Valid @RequestBody CommentRequest request) {
		return ApiResponse.ok(mapper.toResponse(service.add(taskId, request.body())));
	}

	@DeleteMapping("/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long taskId, @PathVariable Long commentId) {
		service.delete(taskId, commentId);
	}
}
