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

	/** Người đang hoạt động, không phải Admin, khớp tên/email/vị trí (không phân biệt hoa thường). */
	@org.springframework.data.jpa.repository.Query("""
			select u from User u
			where u.status = com.soudan.user.domain.UserStatus.ACTIVE
			  and u.systemRole <> com.soudan.common.security.SystemRole.ADMIN
			  and (:q = '' or lower(u.fullName) like concat('%', :q, '%')
			       or lower(u.email) like concat('%', :q, '%')
			       or lower(coalesce(u.position, '')) like concat('%', :q, '%'))
			order by u.fullName asc, u.id asc""")
	java.util.List<User> searchPeople(@org.springframework.data.repository.query.Param("q") String q,
			org.springframework.data.domain.Pageable pageable);
}
