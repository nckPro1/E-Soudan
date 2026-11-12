package com.soudan.task.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soudan.task.domain.TaskHistory;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

	List<TaskHistory> findByTaskIdOrderByChangedAtAscIdAsc(Long taskId);
}
