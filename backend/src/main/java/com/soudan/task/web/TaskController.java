package com.soudan.task.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.soudan.common.api.ApiResponse;
import com.soudan.common.api.PageQuery;
import com.soudan.common.api.PageResponse;
import com.soudan.common.exception.ValidationException;
import com.soudan.task.mapper.TaskMapper;
import com.soudan.task.service.TaskCommand;
import com.soudan.task.service.TaskService;
import com.soudan.task.service.TaskSubmissionService;
import com.soudan.task.web.TaskResponse.SubmissionResponse;

import jakarta.validation.Valid;

/**
 * Task. Controller mỏng: quyền theo vai trò trong nhóm kiểm tra trong service (cần biết nhóm của task),
 * không tin bất cứ trạng thái nào client gửi lên.
 */
@RestController
@RequestMapping("/api/v1")
public class TaskController {

	private final TaskService taskService;
	private final TaskSubmissionService submissionService;
	private final TaskMapper mapper;

	public TaskController(TaskService taskService, TaskSubmissionService submissionService, TaskMapper mapper) {
		this.taskService = taskService;
		this.submissionService = submissionService;
		this.mapper = mapper;
	}

	@GetMapping("/teams/{teamId}/tasks")
	public ApiResponse<PageResponse<TaskResponse>> listByTeam(@PathVariable Long teamId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String assigneeId,
			@RequestParam(required = false) String priority,
			@RequestParam(required = false) String dueFrom,
			@RequestParam(required = false) String dueTo) {
		PageQuery query = query(page, size, sort, q, status, assigneeId, priority, dueFrom, dueTo);
		return ApiResponse.ok(PageResponse.of(taskService.searchByTeam(teamId, query), mapper::toResponse));
	}

	@GetMapping("/tasks/mine")
	public ApiResponse<PageResponse<TaskResponse>> mine(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String priority,
			@RequestParam(required = false) String dueFrom,
			@RequestParam(required = false) String dueTo) {
		PageQuery query = query(page, size, sort, q, status, null, priority, dueFrom, dueTo);
		return ApiResponse.ok(PageResponse.of(taskService.searchMine(query), mapper::toResponse));
	}

	@PostMapping("/teams/{teamId}/tasks")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<TaskResponse> create(@PathVariable Long teamId, @Valid @RequestBody TaskRequest request) {
		return ApiResponse.ok(mapper.toResponse(taskService.create(teamId, command(request))));
	}

	@GetMapping("/tasks/{id}")
	public ApiResponse<TaskResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(mapper.toResponse(taskService.get(id)));
	}

	@PutMapping("/tasks/{id}")
	public ApiResponse<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
		if (request.version() == null) {
			throw new ValidationException("version là bắt buộc khi sửa task");
		}
		return ApiResponse.ok(mapper.toResponse(taskService.update(id, request.version(), command(request))));
	}

	@PostMapping("/tasks/{id}/start")
	public ApiResponse<TaskResponse> start(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
		return ApiResponse.ok(mapper.toResponse(taskService.start(id, request.version())));
	}

	@PostMapping("/tasks/{id}/submit-for-review")
	public ApiResponse<TaskResponse> submitForReview(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
		return ApiResponse.ok(mapper.toResponse(taskService.submitForReview(id, request.version())));
	}

	@PostMapping("/tasks/{id}/approve")
	public ApiResponse<TaskResponse> approve(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
		return ApiResponse.ok(mapper.toResponse(taskService.approve(id, request.version())));
	}

	@PostMapping("/tasks/{id}/request-revision")
	public ApiResponse<TaskResponse> requestRevision(@PathVariable Long id, @Valid @RequestBody RevisionRequest request) {
		return ApiResponse.ok(mapper.toResponse(taskService.requestRevision(id, request.version(), request.reason())));
	}

	@GetMapping("/tasks/{id}/history")
	public ApiResponse<List<HistoryResponse>> history(@PathVariable Long id) {
		return ApiResponse.ok(mapper.toHistory(taskService.history(id)));
	}

	@PostMapping(path = "/tasks/{id}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SubmissionResponse> submit(@PathVariable Long id, @RequestParam("file") MultipartFile file)
			throws IOException {
		var saved = submissionService.submit(id, file.getOriginalFilename(), file.getSize(), file.getInputStream());
		return ApiResponse.ok(mapper.toResponse(saved));
	}

	@GetMapping("/tasks/{id}/submissions/{submissionId}/download")
	public ResponseEntity<InputStreamResource> download(@PathVariable Long id, @PathVariable Long submissionId) {
		var file = submissionService.download(id, submissionId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.sizeBytes())
				.body(new InputStreamResource(file.content()));
	}

	private static TaskCommand command(TaskRequest r) {
		return new TaskCommand(r.title(), r.description(), r.assigneeId(), r.dueDate(), r.priority(), r.requireFile());
	}

	private static PageQuery query(int page, int size, String sort, String q, String status, String assigneeId,
			String priority, String dueFrom, String dueTo) {
		Map<String, String> filters = new HashMap<>();
		put(filters, "q", q);
		put(filters, "status", status);
		put(filters, "assigneeId", assigneeId);
		put(filters, "priority", priority);
		put(filters, "dueFrom", dueFrom);
		put(filters, "dueTo", dueTo);
		return new PageQuery(page, size, sort, filters);
	}

	private static void put(Map<String, String> filters, String key, String value) {
		if (value != null && !value.isBlank()) {
			filters.put(key, value);
		}
	}
}
