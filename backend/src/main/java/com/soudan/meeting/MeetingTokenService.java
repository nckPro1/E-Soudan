package com.soudan.meeting;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;

/** Tạo mã phòng và ký token JaaS (RS256). Chủ trì có quyền moderator nên duyệt được người trong phòng chờ. */
@Service
public class MeetingTokenService {

	/** Mã phòng do hệ thống sinh: chữ thường và số, đủ dài để không đoán được. */
	private static final Pattern ROOM = Pattern.compile("[a-z0-9]{12,32}");
	private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

	private final MeetingProperties properties;
	private final SecureRandom random = new SecureRandom();
	private volatile RSASSASigner signer;

	public MeetingTokenService(MeetingProperties properties) {
		this.properties = properties;
	}

	public boolean configured() {
		return properties.configured();
	}

	public String newRoom() {
		StringBuilder sb = new StringBuilder(16);
		for (int i = 0; i < 16; i++) {
			sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}

	public static boolean validRoom(String room) {
		return room != null && ROOM.matcher(room).matches();
	}

	/** Làm sạch tên hiển thị: bỏ ký tự điều khiển, cắt gọn; trống thì dùng tên mặc định. */
	static String cleanName(String name, String fallback) {
		String n = name == null ? "" : name.replaceAll("\\p{Cntrl}", " ").trim();
		if (n.length() > 60) {
			n = n.substring(0, 60).trim();
		}
		return n.isEmpty() ? fallback : n;
	}

	public String sign(String room, String name, String email, boolean moderator) {
		if (!configured()) {
			throw new BusinessException(ErrorCode.MEETING_NOT_CONFIGURED);
		}
		try {
			Instant now = Instant.now();
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.audience("jitsi")
					.issuer("chat")
					.subject(properties.appId())
					.issueTime(Date.from(now))
					.notBeforeTime(Date.from(now.minusSeconds(10)))
					.expirationTime(Date.from(now.plus(properties.tokenTtl())))
					.claim("room", room)
					.claim("context", Map.of(
							"user", Map.of(
									"name", cleanName(name, moderator ? "Chủ trì" : "Khách"),
									"email", email == null ? "" : email,
									"moderator", moderator ? "true" : "false"),
							"features", Map.of(
									"livestreaming", false,
									"recording", false,
									"transcription", false,
									"outbound-call", false)))
					.build();
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
					.keyID(properties.keyId()).type(JOSEObjectType.JWT).build(), claims);
			jwt.sign(signer());
			return jwt.serialize();
		}
		catch (JOSEException e) {
			throw new IllegalStateException("Không ký được token cuộc họp", e);
		}
	}

	private RSASSASigner signer() {
		RSASSASigner s = signer;
		if (s == null) {
			synchronized (this) {
				if (signer == null) {
					signer = new RSASSASigner(parseKey(properties.privateKey()));
				}
				s = signer;
			}
		}
		return s;
	}

	/**
	 * Nhận khoá PKCS#8 ở một trong ba dạng: PEM nguyên văn, PEM đã base64 hoá toàn bộ, hoặc chỉ phần nội dung khoá (DER) mã
	 * base64. Hai dạng sau để cất trên một dòng trong biến môi trường/SSM; dạng cuối gọn nhất (khoá 4096 bit vẫn dưới 4 KB).
	 */
	static PrivateKey parseKey(String raw) {
		try {
			String text = raw.trim();
			if (!text.contains("BEGIN")) {
				byte[] decoded = Base64.getMimeDecoder().decode(text);
				String asText = new String(decoded, StandardCharsets.UTF_8);
				if (!asText.contains("BEGIN")) {
					return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
				}
				text = asText;
			}
			if (text.contains("BEGIN RSA PRIVATE KEY")) {
				throw new IllegalStateException("Khoá riêng phải ở dạng PKCS#8 (BEGIN PRIVATE KEY)");
			}
			String body = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
			return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
		}
		catch (IllegalStateException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("Khoá riêng của JaaS không đọc được", e);
		}
	}
}
