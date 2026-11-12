package com.soudan.user;

import java.util.Optional;

/**
 * Giao diện công khai của module user cho module khác (ví dụ auth) gọi.
 * Module khác không được import entity hay repository của user (ARCHITECTURE.md 4.2).
 */
public interface UserApi {

	Optional<UserAccount> findAccountByEmail(String email);

	Optional<UserAccount> findAccountById(Long id);
}
