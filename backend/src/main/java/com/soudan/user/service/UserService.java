package com.soudan.user.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.api.PageQuery;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.BusinessException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.search.SearchSpecification;
import com.soudan.common.search.SearchWhitelist;
import com.soudan.common.search.SearchWhitelist.FilterRule;
import com.soudan.common.security.SystemRole;
import com.soudan.user.UserAccount;
import com.soudan.user.UserApi;
import com.soudan.user.UserLockedEvent;
import com.soudan.user.UserSummary;
import com.soudan.user.domain.User;
import com.soudan.user.domain.UserStatus;
import com.soudan.user.repository.UserRepository;

import java.util.Map;
import java.util.Set;

/**
 * Nghiệp vụ tài khoản. {@code @Transactional} đặt ở đây (ARCHITECTURE.md 4.1): mỗi phương thức
 * là một giao dịch; lỗi giữa chừng thì toàn bộ thay đổi bị huỷ.
 */
@Service
@Transactional
public class UserService implements UserApi {

	/** Tên tham số lọc/sắp xếp mà API users cho phép. */
	static final SearchWhitelist WHITELIST = new SearchWhitelist(
			Map.of(
					"q", FilterRule.contains("fullName", "email", "position"),
					"status", FilterRule.equalsTo("status"),
					"role", FilterRule.equalsTo("systemRole")),
			Set.of("fullName", "email", "createdAt", "status", "systemRole"));

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher events;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
	}

	/** Admin tạo tài khoản. Chỉ gán được USER hoặc MANAGER qua API. */
	public User create(CreateUserCommand command) {
		if (command.systemRole() == SystemRole.ADMIN) {
			throw new ValidationException("Không tạo tài khoản Admin qua API");
		}
		return createInternal(command);
	}

	/** Admin đầu tiên (khởi động hệ thống). Không đi qua API nên được phép role ADMIN. */
	public User createInitialAdmin(String email, String password, String fullName) {
		return createInternal(new CreateUserCommand(email, password, fullName, null, SystemRole.ADMIN));
	}

	@Transactional(readOnly = true)
	public boolean hasAnyAdmin() {
		return users.existsBySystemRole(SystemRole.ADMIN);
	}

	@Transactional(readOnly = true)
	public Page<User> search(PageQuery query) {
		Specification<User> spec = SearchSpecification.of(query.filters(), WHITELIST);
		return users.findAll(spec, SearchSpecification.pageable(query, WHITELIST, Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@Transactional(readOnly = true)
	public User get(Long id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản " + id));
	}

	public User updateProfile(Long id, long version, String fullName, String position) {
		User user = loadForUpdate(id, version);
		user.updateProfile(fullName, position);
		return users.saveAndFlush(user);
	}

	public User changeRole(Long id, long version, SystemRole role) {
		if (role == SystemRole.ADMIN) {
			throw new ValidationException("Không gán vai trò Admin qua API");
		}
		User user = loadForUpdate(id, version);
		if (user.getSystemRole() != role) {
			assertNotLastActiveAdmin(user);
			user.changeRole(role);
		}
		return users.saveAndFlush(user);
	}

	public User lock(Long id) {
		User user = get(id);
		if (user.isActive()) {
			assertNotLastActiveAdmin(user);
			user.lock();
			users.saveAndFlush(user);
			events.publishEvent(new UserLockedEvent(user.getId()));
		}
		return user;
	}

	public User unlock(Long id) {
		User user = get(id);
		if (!user.isActive()) {
			user.unlock();
			users.saveAndFlush(user);
		}
		return user;
	}

	/** Người dùng tự đổi mật khẩu: phải đúng mật khẩu cũ, mật khẩu mới phải khác. */
	public void changePassword(Long userId, String currentPassword, String newPassword) {
		User user = get(userId);
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new ValidationException("Mật khẩu mới phải khác mật khẩu hiện tại");
		}
		user.setPassword(passwordEncoder.encode(newPassword), false);
		users.saveAndFlush(user);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UserAccount> findAccountByEmail(String email) {
		return users.findByEmailIgnoreCase(User.normalizeEmail(email)).map(UserService::toAccount);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UserAccount> findAccountById(Long id) {
		return users.findById(id).map(UserService::toAccount);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserSummary> findSummariesByIds(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return users.findAllById(ids).stream()
				.map(u -> new UserSummary(u.getId(), u.getFullName(), u.getPosition(), u.getSystemRole(), u.isActive()))
				.toList();
	}

	private User createInternal(CreateUserCommand command) {
		String email = User.normalizeEmail(command.email());
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		User user = User.create(email, passwordEncoder.encode(command.password()), command.fullName(),
				command.position(), command.systemRole());
		return users.saveAndFlush(user);
	}

	/** Nạp user và so phiên bản client gửi lên với phiên bản hiện tại (khoá lạc quan, trả 409 nếu lệch). */
	private User loadForUpdate(Long id, long version) {
		User user = get(id);
		if (user.getVersion() != version) {
			throw new ConflictException(ErrorCode.VERSION_CONFLICT);
		}
		return user;
	}

	/** Hệ thống phải luôn còn ít nhất một Admin đang hoạt động. */
	private void assertNotLastActiveAdmin(User target) {
		if (target.getSystemRole() == SystemRole.ADMIN && target.getStatus() == UserStatus.ACTIVE
				&& users.countBySystemRoleAndStatus(SystemRole.ADMIN, UserStatus.ACTIVE) <= 1) {
			throw new ConflictException(ErrorCode.LAST_ADMIN_PROTECTED);
		}
	}

	private static UserAccount toAccount(User user) {
		return new UserAccount(user.getId(), user.getEmail(), user.getPasswordHash(), user.getFullName(),
				user.getSystemRole(), user.isActive(), user.isMustChangePassword());
	}
}
