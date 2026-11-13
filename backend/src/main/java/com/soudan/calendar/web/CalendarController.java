package com.soudan.calendar.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import com.soudan.calendar.domain.CalendarNote;
import com.soudan.calendar.service.CalendarService;
import com.soudan.common.api.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Lịch cá nhân của người đang đăng nhập. Mọi vai trò dùng được; mỗi người chỉ thấy ghi chú của mình. */
@RestController
@RequestMapping("/api/v1/calendar/notes")
public class CalendarController {

	public record NoteRequest(@NotNull LocalDate date, @NotBlank @Size(max = CalendarNote.MAX_TITLE) String title,
			@Size(max = CalendarNote.MAX_CONTENT) String content, String color) {
	}

	public record NoteResponse(Long id, LocalDate date, String title, String content, String color) {
		static NoteResponse of(CalendarNote n) {
			return new NoteResponse(n.getId(), n.getNoteDate(), n.getTitle(), n.getContent(), n.getColor());
		}
	}

	private final CalendarService service;

	public CalendarController(CalendarService service) {
		this.service = service;
	}

	/** Ghi chú trong khoảng ngày [from, to], theo thứ tự ngày rồi thứ tự tạo. */
	@GetMapping
	public ApiResponse<List<NoteResponse>> list(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.ok(service.list(from, to).stream().map(NoteResponse::of).toList());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<NoteResponse> create(@Valid @RequestBody NoteRequest request) {
		return ApiResponse.ok(NoteResponse.of(
				service.create(request.date(), request.title(), request.content(), request.color())));
	}

	@PutMapping("/{id}")
	public ApiResponse<NoteResponse> update(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
		return ApiResponse.ok(NoteResponse.of(
				service.update(id, request.date(), request.title(), request.content(), request.color())));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ApiResponse.ok(null);
	}
}
