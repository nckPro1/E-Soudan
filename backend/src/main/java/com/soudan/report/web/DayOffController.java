package com.soudan.report.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.common.api.ApiResponse;
import com.soudan.report.domain.ReportDayOff;
import com.soudan.report.service.DayOffService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Báo nghỉ của người đang đăng nhập (ngày nghỉ không bị tính là chưa nộp báo cáo). */
@RestController
@RequestMapping("/api/v1/report-days-off")
public class DayOffController {

	public record DayOffRequest(@Size(max = ReportDayOff.MAX_REASON) String reason) {
	}

	public record DayOffResponse(LocalDate day, String reason) {
		static DayOffResponse of(ReportDayOff d) {
			return new DayOffResponse(d.getDay(), d.getReason());
		}
	}

	private final DayOffService service;

	public DayOffController(DayOffService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<List<DayOffResponse>> list(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.ok(service.list(from, to).stream().map(DayOffResponse::of).toList());
	}

	@PutMapping("/{day}")
	public ApiResponse<DayOffResponse> mark(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
			@Valid @RequestBody(required = false) DayOffRequest request) {
		return ApiResponse.ok(DayOffResponse.of(service.mark(day, request == null ? null : request.reason())));
	}

	@DeleteMapping("/{day}")
	public ApiResponse<Void> unmark(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
		service.unmark(day);
		return ApiResponse.ok(null);
	}
}
