package com.soudan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.soudan.common.exception.BusinessException;
import com.soudan.meeting.MeetingProperties;
import com.soudan.meeting.MeetingTokenService;

/** Token JaaS: ký đúng khoá, chủ trì có quyền moderator còn khách thì không, chấp nhận PEM lẫn PEM đã base64. */
class MeetingTokenServiceTest {

	private static final String APP_ID = "vpaas-magic-cookie-test";
	private static final String KEY_ID = APP_ID + "/abc123";

	static KeyPair newKeyPair() throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
		g.initialize(2048);
		return g.generateKeyPair();
	}

	static String pem(KeyPair pair) {
		return "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
	}

	private static MeetingTokenService service(String privateKey) {
		return new MeetingTokenService(new MeetingProperties(APP_ID, KEY_ID, privateKey, "8x8.vc", Duration.ofHours(3)));
	}

	@Test
	void should_signModeratorToken_withExpectedClaims() throws Exception {
		KeyPair pair = newKeyPair();
		MeetingTokenService tokens = service(pem(pair));

		SignedJWT jwt = SignedJWT.parse(tokens.sign("room12345678abcd", "Cô Lan", "lan@x.vn", true));

		assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
		assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
		assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(APP_ID);
		assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("jitsi");
		assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("chat");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("room")).isEqualTo("room12345678abcd");
		assertThat(user(jwt)).containsEntry("moderator", "true").containsEntry("name", "Cô Lan");
		assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isAfter(jwt.getJWTClaimsSet().getIssueTime());
	}

	@Test
	void should_signGuestToken_withoutModeratorRights() throws Exception {
		MeetingTokenService tokens = service(pem(newKeyPair()));

		SignedJWT jwt = SignedJWT.parse(tokens.sign("room12345678abcd", "  ", null, false));

		assertThat(user(jwt)).containsEntry("moderator", "false").containsEntry("name", "Khách");
	}

	@Test
	void should_acceptBase64EncodedPem() throws Exception {
		KeyPair pair = newKeyPair();
		String oneLine = Base64.getEncoder().encodeToString(pem(pair).getBytes());

		SignedJWT jwt = SignedJWT.parse(service(oneLine).sign("room12345678abcd", "A", null, false));

		assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
	}

	@Test
	void should_acceptBareBase64Der_forSsmSizeLimit() throws Exception {
		KeyPair pair = newKeyPair();
		String bare = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

		SignedJWT jwt = SignedJWT.parse(service(bare).sign("room12345678abcd", "A", null, false));

		assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
	}

	@Test
	void should_refuseToSign_whenNotConfigured() {
		MeetingTokenService tokens = new MeetingTokenService(
				new MeetingProperties("", "", "", "8x8.vc", Duration.ofHours(3)));

		assertThat(tokens.configured()).isFalse();
		assertThatThrownBy(() -> tokens.sign("room12345678abcd", "A", null, false)).isInstanceOf(BusinessException.class);
	}

	@Test
	void should_generateUnguessableValidRoomCodes() {
		MeetingTokenService tokens = service("x");

		String a = tokens.newRoom();
		String b = tokens.newRoom();

		assertThat(a).isNotEqualTo(b);
		assertThat(MeetingTokenService.validRoom(a)).isTrue();
		assertThat(MeetingTokenService.validRoom("ab")).isFalse();
		assertThat(MeetingTokenService.validRoom("../etc/passwd")).isFalse();
		assertThat(MeetingTokenService.validRoom("ROOM12345678ABCD")).isFalse();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> user(SignedJWT jwt) throws Exception {
		Map<String, Object> context = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("context");
		return (Map<String, Object>) context.get("user");
	}
}
