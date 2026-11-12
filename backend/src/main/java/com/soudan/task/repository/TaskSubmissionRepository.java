package com.soudan.task.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soudan.task.domain.TaskSubmission;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

	List<TaskSubmission> findByTaskIdOrderByUploadedAtAscIdAsc(Long taskId);

	boolean existsByTaskId(Long taskId);

	Optional<TaskSubmission> findByIdAndTaskId(Long id, Long taskId);
}
