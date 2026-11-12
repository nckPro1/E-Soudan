package com.soudan.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình thông báo ({@code app.notification.*}).
 *
 * @param from địa chỉ người gửi
 * @param frontendUrl địa chỉ gốc của web, dùng dựng đường dẫn trong email (không có dấu / ở cuối)
 */
@ConfigurationProperties("app.notification")
public record NotificationProperties(
		@DefaultValue("no-reply@soudan.local") String from,
		@DefaultValue("http://localhost:5173") String frontendUrl) {
}
