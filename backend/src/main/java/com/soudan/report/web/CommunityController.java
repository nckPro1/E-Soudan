package com.soudan.report.web;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import com.soudan.common.api.ApiResponse;
import com.soudan.common.api.PageResponse;
import com.soudan.report.service.CommunityService;
import com.soudan.report.service.ReportService;
import com.soudan.report.service.ReportService.NewFile;
import com.soudan.report.web.ReportDtos.AddMembersRequest;
import com.soudan.report.web.ReportDtos.CommunityDetailResponse;
import com.soudan.report.web.ReportDtos.CommunityRequest;
import com.soudan.report.web.ReportDtos.CommunitySummaryResponse;
import com.soudan.report.web.ReportDtos.PendingResponse;
import com.soudan.report.web.ReportDtos.PostResponse;

import jakarta.validation.Valid;


/** Cộng đồng báo cáo và feed báo cáo của cộng đồng. Quyền kiểm tra trong service (chủ cộng đồng / thành viên). */
@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {

	private final CommunityService communities;
	private final ReportService reports;

	public CommunityController(CommunityService communities, ReportService reports) {
		this.communities = communities;
		this.reports = reports;
	}

	@GetMapping
	public ApiResponse<List<CommunitySummaryResponse>> listMine() {
		return ApiResponse.ok(communities.listMine().stream().map(CommunitySummaryResponse::of).toList());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CommunityDetailResponse> create(@Valid @RequestBody CommunityRequest request) {
		return ApiResponse.ok(CommunityDetailResponse.of(communities.create(request.name(), request.description())));
	}

	@GetMapping("/{id}")
	public ApiResponse<CommunityDetailResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(CommunityDetailResponse.of(communities.get(id)));
	}

	@PutMapping("/{id}")
	public ApiResponse<CommunityDetailResponse> update(@PathVariable Long id,
			@Valid @RequestBody CommunityRequest request) {
		return ApiResponse.ok(CommunityDetailResponse.of(communities.update(id, request.name(), request.description())));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		communities.delete(id);
	}

	@PostMapping("/{id}/members")
	public ApiResponse<CommunityDetailResponse> addMembers(@PathVariable Long id,
			@Valid @RequestBody AddMembersRequest request) {
		return ApiResponse.ok(CommunityDetailResponse.of(communities.addMembers(id, request.userIds())));
	}

	@DeleteMapping("/{id}/members/{userId}")
	public ApiResponse<CommunityDetailResponse> removeMember(@PathVariable Long id, @PathVariable Long userId) {
		return ApiResponse.ok(CommunityDetailResponse.of(communities.removeMember(id, userId)));
	}

	/** Thành viên chưa nộp báo cáo trong ngày (mặc định hôm nay); chỉ chủ cộng đồng. */
	@GetMapping("/{id}/pending")
	public ApiResponse<PendingResponse> pending(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ApiResponse.ok(PendingResponse.of(communities.pending(id, date)));
	}

	/** Báo cáo của MỘT thành viên ({@code authorId} bắt buộc): không có danh sách chung của cả cộng đồng. */
	@GetMapping("/{id}/posts")
	public ApiResponse<PageResponse<PostResponse>> feed(@PathVariable Long id, @RequestParam Long authorId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(PageResponse.of(reports.feed(id, authorId, page, size), PostResponse::of));
	}

	/**
	 * Nộp báo cáo hôm nay (đã nộp thì cập nhật). Chỉ thành viên. multipart/form-data: {@code title}, {@code body}
	 * (HTML, được lọc ở server), {@code files} (0..10 file/ảnh mới), {@code removeAttachmentIds} (file cũ cần gỡ).
	 */
	@PostMapping(path = "/{id}/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PostResponse> submit(@PathVariable Long id, @RequestParam("title") String title,
			@RequestParam(value = "body", required = false) String body,
			@RequestParam(value = "files", required = false) List<MultipartFile> files,
			@RequestParam(value = "removeAttachmentIds", required = false) List<Long> removeAttachmentIds)
			throws IOException {
		List<NewFile> uploads = new ArrayList<>();
		if (files != null) {
			for (MultipartFile f : files) {
				if (!f.isEmpty()) {
					uploads.add(new NewFile(f.getOriginalFilename(), f.getSize(), f.getInputStream()));
				}
			}
		}
		return ApiResponse.ok(PostResponse.of(reports.submit(id, title, body, uploads, removeAttachmentIds)));
	}

	/** Báo cáo hôm nay của mình; chưa nộp thì data là null. */
	@GetMapping("/{id}/posts/mine-today")
	public ApiResponse<PostResponse> myToday(@PathVariable Long id) {
		return ApiResponse.ok(reports.myToday(id).map(PostResponse::of).orElse(null));
	}
}
