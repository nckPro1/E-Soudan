package com.soudan.common.base;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Cột chung của mọi bảng nghiệp vụ (ARCHITECTURE.md 5.1).
 * Entity con thêm {@code @SQLRestriction("deleted_at IS NULL")} nếu dùng xoá mềm.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@CreatedBy
	@Column(updatable = false)
	private Long createdBy;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	private Long updatedBy;

	/** Khoá lạc quan: hai người sửa cùng lúc thì người sau nhận xung đột (409). */
	@Version
	@Column(nullable = false)
	private Long version;

	private Instant deletedAt;

	public Long getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public void markDeleted(Instant at) {
		this.deletedAt = at;
	}
}
