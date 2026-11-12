package com.soudan.auth.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình JWT (tiền tố {@code app.jwt}). {@code secret} lấy từ biến môi trường, tối thiểu 32 byte.
 *
 * @param accessTtl thời hạn access token (ngắn)
 * @param refreshTtl thời hạn refresh token (dài hơn)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
