package com.soudan.task.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.task.domain.Task;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

	/** Việc người này đang phụ trách mà chưa xong, hạn gần nhất trước, việc không có hạn xếp cuối (cho trợ lý AI). */
	@Query("""
			select t from Task t
			where t.assigneeId = :me and t.status <> com.soudan.task.domain.TaskStatus.DONE and t.deletedAt is null
			order by case when t.dueDate is null then 1 else 0 end, t.dueDate asc, t.id asc""")
	List<Task> openOf(@Param("me") Long me, Pageable pageable);

	/** Việc chưa xong có hạn trong khoảng [from, to] (cho nhắc hạn). */
	@Query("""
			select t from Task t
			where t.dueDate between :from and :to
			  and t.status <> com.soudan.task.domain.TaskStatus.DONE and t.deletedAt is null
			order by t.dueDate, t.id""")
	List<Task> openDueBetween(@Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

	/** Số việc chưa xong của một người trong một nhóm (chặn gỡ khỏi nhóm). */
	@Query("""
			select count(t) from Task t
			where t.teamId = :teamId and t.assigneeId = :userId
			  and t.status <> com.soudan.task.domain.TaskStatus.DONE and t.deletedAt is null""")
	long countOpenOf(@Param("teamId") Long teamId, @Param("userId") Long userId);

	/** Việc người này phụ trách có thay đổi từ mốc {@code since}. */
	@Query("""
			select t from Task t
			where t.assigneeId = :me and t.updatedAt >= :since and t.deletedAt is null
			order by t.updatedAt desc""")
	List<Task> changedSince(@Param("me") Long me, @Param("since") Instant since, Pageable pageable);

	/** Việc trong các nhóm của người này hoặc giao cho họ, có thay đổi từ mốc {@code since} (cho truy xuất của AI). */
	@Query("""
			select t from Task t
			where (t.teamId in :teamIds or t.assigneeId = :me) and t.updatedAt >= :since and t.deletedAt is null
			order by t.updatedAt desc""")
	List<Task> visibleSince(@Param("teamIds") Collection<Long> teamIds, @Param("me") Long me,
			@Param("since") Instant since, Pageable pageable);
}
