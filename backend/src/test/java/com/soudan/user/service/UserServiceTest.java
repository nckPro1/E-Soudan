package com.soudan.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.security.SystemRole;
import com.soudan.user.UserLockedEvent;
import com.soudan.user.domain.User;
import com.soudan.user.domain.UserStatus;
import com.soudan.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository users;
	@Mock
	private ApplicationEventPublisher events;

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(users, encoder, events);
	}

	private User existing(long id, SystemRole role, long version) {
		User user = User.create("u" + id + "@example.com", encoder.encode("OldPass1234"), "User " + id, null, role);
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "version", version);
		return user;
	}

	@Test
	void should_throwEmailExists_when_emailAlreadyUsed() {
		when(users.existsByEmailIgnoreCase("a@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new CreateUserCommand(" A@Example.com ", "Passw0rd1234", "A", null, SystemRole.USER)))
				.isInstanceOf(ConflictException.class)
				.extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void should_storeLowercaseTrimmedEmailAndHashedPassword_when_creating() {
		when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

		User created = service.create(new CreateUserCommand("  New@Example.COM ", "Passw0rd1234", " Name ", "  ", SystemRole.MANAGER));

		assertThat(created.getEmail()).isEqualTo("new@example.com");
		assertThat(created.getPasswordHash()).isNotEqualTo("Passw0rd1234");
		assertThat(encoder.matches("Passw0rd1234", created.getPasswordHash())).isTrue();
		assertThat(created.getPosition()).isNull();
		assertThat(created.isMustChangePassword()).isTrue();
		assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void should_rejectAdminRole_when_creatingViaApiMethod() {
		assertThatThrownBy(() -> service.create(new CreateUserCommand("a@example.com", "Passw0rd1234", "A", null, SystemRole.ADMIN)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void should_refuseToLock_when_targetIsLastActiveAdmin() {
		User admin = existing(1, SystemRole.ADMIN, 0);
		when(users.findById(1L)).thenReturn(Optional.of(admin));
		when(users.countBySystemRoleAndStatus(SystemRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

		assertThatThrownBy(() -> service.lock(1L))
				.isInstanceOf(ConflictException.class)
				.extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);
		assertThat(admin.isActive()).isTrue();
		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	void should_lockAndPublishEvent_when_targetIsOrdinaryUser() {
		User user = existing(2, SystemRole.USER, 0);
		when(users.findById(2L)).thenReturn(Optional.of(user));

		service.lock(2L);

		assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new UserLockedEvent(2L));
	}

	@Test
	void should_doNothing_when_lockingAlreadyLockedUser() {
		User user = existing(2, SystemRole.USER, 0);
		user.lock();
		when(users.findById(2L)).thenReturn(Optional.of(user));

		service.lock(2L);

		verify(events, never()).publishEvent(any(Object.class));
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void should_refuseDemotion_when_targetIsLastActiveAdmin() {
		User admin = existing(1, SystemRole.ADMIN, 5);
		when(users.findById(1L)).thenReturn(Optional.of(admin));
		when(users.countBySystemRoleAndStatus(SystemRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

		assertThatThrownBy(() -> service.changeRole(1L, 5, SystemRole.USER))
				.isInstanceOf(ConflictException.class);
		assertThat(admin.getSystemRole()).isEqualTo(SystemRole.ADMIN);
	}

	@Test
	void should_throwVersionConflict_when_clientVersionIsStale() {
		User user = existing(2, SystemRole.USER, 3);
		when(users.findById(2L)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.updateProfile(2L, 2, "New name", null))
				.isInstanceOf(ConflictException.class)
				.extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT);
		assertThat(user.getFullName()).isEqualTo("User 2");
	}

	@Test
	void should_rejectChange_when_currentPasswordWrong() {
		User user = existing(2, SystemRole.USER, 0);
		when(users.findById(2L)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.changePassword(2L, "WrongPass1234", "NewPass123456"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.CURRENT_PASSWORD_INCORRECT);
	}

	@Test
	void should_rejectChange_when_newPasswordSameAsCurrent() {
		User user = existing(2, SystemRole.USER, 0);
		when(users.findById(2L)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.changePassword(2L, "OldPass1234", "OldPass1234"))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void should_clearMustChangeFlag_when_passwordChanged() {
		User user = existing(2, SystemRole.USER, 0);
		when(users.findById(2L)).thenReturn(Optional.of(user));

		service.changePassword(2L, "OldPass1234", "NewPass123456");

		assertThat(user.isMustChangePassword()).isFalse();
		assertThat(encoder.matches("NewPass123456", user.getPasswordHash())).isTrue();
	}
}
