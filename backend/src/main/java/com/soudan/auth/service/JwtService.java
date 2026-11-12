package com.soudan.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.UnauthorizedException;

/**
 * Tạo và kiểm tra access token (JWT ký HS256). Access token chỉ chứa id người dùng ({@code sub}) và thời hạn;
 * vai trò và trạng thái luôn đọc lại từ CSDL ở mỗi request để khoá tài khoản hay đổi vai trò có hiệu lực ngay.
 */
@Service
public class JwtService {

	private static final int MIN_SECRET_BYTES = 32;

	private final JwtEncoder encoder;
	private final JwtDecoder decoder;
	private final Clock clock;
	private final Duration accessTtl;

	public JwtService(JwtProperties properties, Clock clock) {
		byte[] secret = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MIN_SECRET_BYTES) {
			// Thiếu hoặc quá ngắn thì không cho khởi động (đặc biệt ở prod, nơi không có giá trị mặc định).
			throw new IllegalStateException("app.jwt.secret (JWT_SECRET) phải có ít nhất " + MIN_SECRET_BYTES + " byte");
		}
		SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
		timestampValidator.setClock(clock);
		nimbus.setJwtValidator(timestampValidator);
		this.decoder = nimbus;
		this.clock = clock;
		this.accessTtl = properties.accessTtl();
	}

	public String createAccessToken(Long userId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plus(accessTtl))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public long accessTtlSeconds() {
		return accessTtl.toSeconds();
	}

	/** Trả id người dùng trong token; hết hạn thì TOKEN_EXPIRED, sai chữ ký hoặc hỏng thì UNAUTHORIZED. */
	public Long parseUserId(String token) {
		try {
			Jwt jwt = decoder.decode(token);
			return Long.valueOf(jwt.getSubject());
		} catch (JwtException e) {
			boolean expired = e.getMessage() != null && e.getMessage().contains("expired");
			throw new UnauthorizedException(expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED);
		} catch (NumberFormatException e) {
			throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
		}
	}
}
