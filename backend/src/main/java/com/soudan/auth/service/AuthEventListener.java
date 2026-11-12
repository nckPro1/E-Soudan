package com.soudan.auth.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.soudan.user.UserLockedEvent;

/** Phản ứng với sự kiện của module user thay vì để user gọi ngược lại auth (tránh vòng phụ thuộc). */
@Component
class AuthEventListener {

	private final AuthService authService;

	AuthEventListener(AuthService authService) {
		this.authService = authService;
	}

	@EventListener
	void on(UserLockedEvent event) {
		authService.revokeAllSessions(event.userId());
	}
}
