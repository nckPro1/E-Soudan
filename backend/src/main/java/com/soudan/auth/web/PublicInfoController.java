package com.soudan.auth.web;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soudan.auth.service.OperatingHoursProperties;
import com.soudan.common.api.ApiResponse;

/**
 * Thông tin công khai cho trang đăng nhập (không cần đăng nhập, không chứa bí mật): hiện tại là giờ hoạt động của hệ thống
 * khi máy chủ chạy theo lịch. {@code operatingHours} là null khi máy chạy cả ngày.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicInfoController {

	public record OperatingHours(String start, String end, List<DayOfWeek> days, String timezone) {
	}

	public record PublicInfoResponse(OperatingHours operatingHours) {
	}

	private final OperatingHoursProperties hours;

	public PublicInfoController(OperatingHoursProperties hours) {
		this.hours = hours;
	}

	@GetMapping("/info")
	public ApiResponse<PublicInfoResponse> info() {
		return ApiResponse.ok(new PublicInfoResponse(hours.enabled()
				? new OperatingHours(hours.start(), hours.end(), hours.days(), hours.timezone())
				: null));
	}
}
