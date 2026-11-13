package com.soudan.report.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một người được thêm vào cộng đồng (Member hoặc Leader đều như nhau). Duy nhất theo (cộng đồng, người). */
@Entity
@Table(name = "community_member")
public class CommunityMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long communityId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Instant addedAt;

	protected CommunityMember() {
		// JPA
	}

	public CommunityMember(Long communityId, Long userId, Instant addedAt) {
		this.communityId = communityId;
		this.userId = userId;
		this.addedAt = addedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getCommunityId() {
		return communityId;
	}

	public Long getUserId() {
		return userId;
	}

	public Instant getAddedAt() {
		return addedAt;
	}
}
