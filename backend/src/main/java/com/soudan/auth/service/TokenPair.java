package com.soudan.auth.service;

/** Cặp token trả cho client sau khi đăng nhập hoặc làm mới. */
public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds, boolean mustChangePassword) {
}
