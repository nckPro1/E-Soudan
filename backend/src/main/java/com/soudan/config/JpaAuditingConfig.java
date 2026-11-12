package com.soudan.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.soudan.common.security.AuthenticatedUser;
import com.soudan.common.security.CurrentUser;

/** Tự điền created_at/created_by/updated_at/updated_by cho entity kế thừa BaseEntity. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	/** Người tạo/sửa là người đăng nhập hiện tại; rỗng khi chạy ngoài request (ví dụ tạo Admin lúc khởi động). */
	@Bean
	AuditorAware<Long> auditorAware(CurrentUser currentUser) {
		return () -> currentUser.find().map(AuthenticatedUser::id);
	}

	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(Instant.now(clock));
	}
}
