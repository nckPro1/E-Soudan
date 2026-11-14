package com.soudan.auth.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Giờ hoạt động của hệ thống ({@code app.operating-hours.*}), chỉ để HIỂN THỊ cho người dùng ở trang đăng nhập khi máy chủ
 * được bật/tắt theo lịch (ví dụ môi trường demo trên AWS). Việc bật/tắt máy thật do lịch ở hạ tầng quyết định, nên hai nơi
 * phải khớp nhau. Mặc định tắt: máy chạy cả ngày thì không hiện gì.
 *
 * @param enabled  có hiện giờ hoạt động ở trang đăng nhập không
 * @param start    giờ mở, dạng HH:mm
 * @param end      giờ đóng, dạng HH:mm (phải sau giờ mở; không hỗ trợ khoảng qua nửa đêm)
 * @param days     các ngày trong tuần hệ thống hoạt động
 * @param timezone múi giờ của {@code start} và {@code end}
 */
@ConfigurationProperties("app.operating-hours")
public record OperatingHoursProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("08:30") String start,
		@DefaultValue("18:00") String end,
		@DefaultValue({ "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY" }) List<DayOfWeek> days,
		@DefaultValue("Asia/Ho_Chi_Minh") String timezone) {

	/** Kiểm tra cấu hình ngay khi khởi động: sai định dạng hoặc giờ đóng không sau giờ mở thì không chạy. */
	public OperatingHoursProperties {
		LocalTime open = LocalTime.parse(start);
		LocalTime close = LocalTime.parse(end);
		if (!close.isAfter(open)) {
			throw new IllegalArgumentException("app.operating-hours.end phải sau start");
		}
		ZoneId.of(timezone);
	}
}
