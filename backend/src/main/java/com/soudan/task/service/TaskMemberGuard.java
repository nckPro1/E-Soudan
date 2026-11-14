package com.soudan.task.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.security.TeamMemberRemovalGuard;
import com.soudan.task.repository.TaskRepository;

/** Việc chưa xong của một người trong một nhóm: chặn gỡ họ khỏi nhóm cho tới khi chuyển giao hoặc hoàn thành. */
@Component
@Transactional(readOnly = true)
class TaskMemberGuard implements TeamMemberRemovalGuard {

	private final TaskRepository tasks;

	TaskMemberGuard(TaskRepository tasks) {
		this.tasks = tasks;
	}

	@Override
	public long openWorkOf(Long teamId, Long userId) {
		return tasks.countOpenOf(teamId, userId);
	}
}
