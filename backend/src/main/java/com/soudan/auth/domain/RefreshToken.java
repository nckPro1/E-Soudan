package com.soudan.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Refresh token đã băm (SHA-256). Không lưu token thô: lộ CSDL cũng không dùng lại được token.
 * {@code userId} là id thuần, không {@code @ManyToOne}, vì entity User thuộc module khác.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, unique = true)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant revokedAt;

	@Column(nullable = false)
	private Instant createdAt;

	protected RefreshToken() {
		// JPA
	}

	public RefreshToken(Long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public Long getUserId() {
		return userId;
	}
}
