package com.soudan.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.soudan.common.security.SystemRole;
import com.soudan.user.domain.User;
import com.soudan.user.domain.UserStatus;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	long countBySystemRoleAndStatus(SystemRole systemRole, UserStatus status);

	boolean existsBySystemRole(SystemRole systemRole);
}
