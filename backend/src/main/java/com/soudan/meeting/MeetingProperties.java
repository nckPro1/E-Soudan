package com.soudan.meeting;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Cấu hình họp video qua JaaS ({@code app.meeting.*}). Để trống thì tính năng tắt: nút "Cuộc họp mới" báo chưa cấu hình.
 *
 * @param appId      App ID của JaaS (dạng {@code vpaas-magic-cookie-...})
 * @param keyId      mã khoá API (kid, dạng {@code vpaas-magic-cookie-.../abc123}) ứng với khoá riêng bên dưới
 * @param privateKey khoá riêng RSA (PKCS#8) để ký token; chấp nhận PEM hoặc PEM đã mã hoá base64 (tiện cho biến môi trường)
 * @param domain     tên miền JaaS, mặc định {@code 8x8.vc}
 * @param tokenTtl   thời hạn token
 */
@ConfigurationProperties("app.meeting")
public record MeetingProperties(
		@DefaultValue("") String appId,
		@DefaultValue("") String keyId,
		@DefaultValue("") String privateKey,
		@DefaultValue("8x8.vc") String domain,
		@DefaultValue("3h") Duration tokenTtl) {

	public boolean configured() {
		return StringUtils.hasText(appId) && StringUtils.hasText(keyId) && StringUtils.hasText(privateKey);
	}
}
