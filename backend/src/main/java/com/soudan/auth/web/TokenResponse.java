package com.soudan.auth.web;

public record TokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		boolean mustChangePassword) {
}
