package com.soudan.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.auth.domain.RefreshToken;
import com.soudan.auth.repository.RefreshTokenRepository;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.UnauthorizedException;
import com.soudan.user.UserAccount;
import com.soudan.user.UserApi;

/** Đăng nhập, làm mới và đăng xuất. Không import entity của module user: chỉ dùng {@link UserApi}. */
@Service
@Transactional
public class AuthService {

	private static final int REFRESH_TOKEN_BYTES = 32;

	private final UserApi userApi;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final JwtProperties properties;
	private final RefreshTokenRepository refreshTokens;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();
	/** Băm giả để thời gian phản hồi khi email không tồn tại gần bằng khi sai mật khẩu (chống dò email). */
	private final String dummyHash;

	public AuthService(UserApi userApi, PasswordEncoder passwordEncoder, JwtService jwtService,
			JwtProperties properties, RefreshTokenRepository refreshTokens, Clock clock) {
		this.userApi = userApi;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.properties = properties;
		this.refreshTokens = refreshTokens;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	public TokenPair login(String email, String password) {
		UserAccount account = userApi.findAccountByEmail(email).orElse(null);
		if (account == null) {
			passwordEncoder.matches(password, dummyHash);
			throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
		}
		if (!passwordEncoder.matches(password, account.passwordHash())) {
			throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
		}
		// Chỉ báo "bị khoá" sau khi mật khẩu đúng, để người lạ không dò được tài khoản nào đang bị khoá.
		if (!account.active()) {
			throw new ForbiddenException(ErrorCode.ACCOUNT_LOCKED);
		}
		return issue(account);
	}

	/** Đổi refresh token cũ lấy cặp mới (xoay vòng). Token đã bị thu hồi mà bị dùng lại => thu hồi toàn bộ. */
	public TokenPair refresh(String rawRefreshToken) {
		Instant now = clock.instant();
		RefreshToken stored = refreshTokens.findByTokenHash(hash(rawRefreshToken))
				.orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED));
		if (stored.isRevoked()) {
			refreshTokens.revokeAllByUserId(stored.getUserId(), now);
			throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
		}
		if (stored.isExpired(now)) {
			throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED);
		}
		UserAccount account = userApi.findAccountById(stored.getUserId())
				.orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED));
		if (!account.active()) {
			stored.revoke(now);
			throw new ForbiddenException(ErrorCode.ACCOUNT_LOCKED);
		}
		stored.revoke(now);
		return issue(account);
	}

	/** Thu hồi refresh token. Gọi lại hoặc token không tồn tại cũng không lỗi (idempotent). */
	public void logout(String rawRefreshToken) {
		refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(t -> t.revoke(clock.instant()));
	}

	/** Thu hồi mọi phiên của một người dùng (gọi khi tài khoản bị khoá). */
	public void revokeAllSessions(Long userId) {
		refreshTokens.revokeAllByUserId(userId, clock.instant());
	}

	private TokenPair issue(UserAccount account) {
		Instant now = clock.instant();
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		random.nextBytes(bytes);
		String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		refreshTokens.save(new RefreshToken(account.id(), hash(rawRefresh), now.plus(properties.refreshTtl()), now));
		return new TokenPair(jwtService.createAccessToken(account.id()), rawRefresh, jwtService.accessTtlSeconds(),
				account.mustChangePassword());
	}

	private static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
