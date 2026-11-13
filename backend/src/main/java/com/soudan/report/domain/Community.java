package com.soudan.report.domain;

import com.soudan.common.base.BaseEntity;
import com.soudan.common.exception.ValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Cộng đồng báo cáo (ví dụ "Phòng phát triển số 2"). Do một Manager (chủ cộng đồng) tạo và quản lý. */
@Entity
@Table(name = "community")
public class Community extends BaseEntity {

	public static final int MAX_NAME = 100;
	public static final int MAX_DESCRIPTION = 500;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(nullable = false)
	private Long ownerId;

	protected Community() {
		// JPA
	}

	public static Community create(String name, String description, Long ownerId) {
		Community community = new Community();
		community.ownerId = ownerId;
		community.rename(name, description);
		return community;
	}

	public void rename(String newName, String newDescription) {
		if (newName == null || newName.isBlank()) {
			throw new ValidationException("Tên cộng đồng không được để trống");
		}
		String cleanName = newName.strip();
		if (cleanName.length() > MAX_NAME) {
			throw new ValidationException("Tên cộng đồng tối đa " + MAX_NAME + " ký tự");
		}
		String cleanDescription = newDescription == null || newDescription.isBlank() ? null : newDescription.strip();
		if (cleanDescription != null && cleanDescription.length() > MAX_DESCRIPTION) {
			throw new ValidationException("Mô tả tối đa " + MAX_DESCRIPTION + " ký tự");
		}
		this.name = cleanName;
		this.description = cleanDescription;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Long getOwnerId() {
		return ownerId;
	}
}
