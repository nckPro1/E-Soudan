package com.soudan.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Giao diện công khai của module user cho module khác (ví dụ auth, team) gọi.
 * Module khác không được import entity hay repository của user (ARCHITECTURE.md 4.2).
 */
public interface UserApi {

	Optional<UserAccount> findAccountByEmail(String email);

	Optional<UserAccount> findAccountById(Long id);

	/** Tra nhiều người một lượt. Id không tồn tại thì không có trong kết quả. */
	List<UserSummary> findSummariesByIds(Collection<Long> ids);
}
