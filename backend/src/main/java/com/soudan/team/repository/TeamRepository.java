package com.soudan.team.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.team.domain.Team;
import com.soudan.team.domain.TeamStatus;

import jakarta.persistence.LockModeType;

public interface TeamRepository extends JpaRepository<Team, Long>, JpaSpecificationExecutor<Team> {

	boolean existsByNameIgnoreCaseAndStatus(String name, TeamStatus status);

	boolean existsByNameIgnoreCaseAndStatusAndIdNot(String name, TeamStatus status, Long id);

	/**
	 * Nạp nhóm và tăng version khi giao dịch kết thúc. Nhờ vậy hai người cùng sửa thành viên của một nhóm
	 * không ghi đè nhau: người đến sau nhận xung đột phiên bản (409).
	 */
	@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
	@Query("select t from Team t where t.id = :id")
	Optional<Team> findByIdForUpdate(@Param("id") Long id);
}
