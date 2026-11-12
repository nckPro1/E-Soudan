package com.soudan.user.domain;

import java.util.Locale;

import org.hibernate.annotations.SQLRestriction;

import com.soudan.common.base.BaseEntity;
import com.soudan.common.security.SystemRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Tài khoản người dùng. Các quy tắc "đổi trạng thái" nằm trong entity; quy tắc cần dữ liệu ngoài
 * (ví dụ Admin cuối cùng) nằm ở UserService.
 */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User extends BaseEntity {

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String fullName;

	private String position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SystemRole systemRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	@Column(nullable = false)
	private boolean mustChangePassword;

	protected User() {
		// JPA
	}

	public static User create(String email, String passwordHash, String fullName, String position,
			SystemRole systemRole) {
		User user = new User();
		user.email = normalizeEmail(email);
		user.passwordHash = passwordHash;
		user.fullName = fullName.trim();
		user.position = blankToNull(position);
		user.systemRole = systemRole;
		user.status = UserStatus.ACTIVE;
		user.mustChangePassword = true;
		return user;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public void updateProfile(String fullName, String position) {
		this.fullName = fullName.trim();
		this.position = blankToNull(position);
	}

	public void changeRole(SystemRole role) {
		this.systemRole = role;
	}

	public void lock() {
		this.status = UserStatus.LOCKED;
	}

	public void unlock() {
		this.status = UserStatus.ACTIVE;
	}

	/** Đặt mật khẩu mới; {@code mustChange} true khi Admin cấp mật khẩu tạm. */
	public void setPassword(String passwordHash, boolean mustChange) {
		this.passwordHash = passwordHash;
		this.mustChangePassword = mustChange;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getFullName() {
		return fullName;
	}

	public String getPosition() {
		return position;
	}

	public SystemRole getSystemRole() {
		return systemRole;
	}

	public UserStatus getStatus() {
		return status;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
