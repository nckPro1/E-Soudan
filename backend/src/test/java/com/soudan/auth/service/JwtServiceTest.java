package com.soudan.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;

class JwtServiceTest {

	private static final String SECRET = "unit-test-secret-0123456789-0123456789";

	private static JwtProperties props(String secret) {
		return new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7));
	}

	@Test
	void should_failToStart_when_secretTooShort() {
		assertThatThrownBy(() -> new JwtService(props("short"), Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void should_failToStart_when_secretMissing() {
		assertThatThrownBy(() -> new JwtService(props(null), Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void should_returnUserId_when_tokenValid() {
		JwtService service = new JwtService(props(SECRET), Clock.systemUTC());

		assertThat(service.parseUserId(service.createAccessToken(42L))).isEqualTo(42L);
	}

	@Test
	void should_beExpired_when_clockPassesTtl() {
		Instant issued = Instant.parse("2026-01-01T00:00:00Z");
		String token = new JwtService(props(SECRET), Clock.fixed(issued, ZoneOffset.UTC)).createAccessToken(1L);

		JwtService later = new JwtService(props(SECRET), Clock.fixed(issued.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
		assertThatThrownBy(() -> later.parseUserId(token))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
	}

	@Test
	void should_beValid_when_justBeforeTtl() {
		Instant issued = Instant.parse("2026-01-01T00:00:00Z");
		String token = new JwtService(props(SECRET), Clock.fixed(issued, ZoneOffset.UTC)).createAccessToken(1L);

		JwtService almost = new JwtService(props(SECRET), Clock.fixed(issued.plus(Duration.ofMinutes(14)), ZoneOffset.UTC));
		assertThat(almost.parseUserId(token)).isEqualTo(1L);
	}
}
