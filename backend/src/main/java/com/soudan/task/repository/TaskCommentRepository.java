package com.soudan.task.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soudan.task.domain.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

	List<TaskComment> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);

	Optional<TaskComment> findByIdAndTaskId(Long id, Long taskId);
}
