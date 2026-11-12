package com.soudan.task.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.task.domain.Task;
import com.soudan.task.domain.TaskComment;
import com.soudan.task.repository.TaskCommentRepository;
import com.soudan.task.repository.TaskRepository;
import com.soudan.task.service.TaskViews.CommentView;
import com.soudan.task.service.TaskViews.PersonView;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Bình luận trên task. Ai xem được task (Admin và mọi người có mặt trong nhóm) thì đọc và viết được;
 * chỉ tác giả hoặc Leader của nhóm mới xoá được.
 */
@Service
@Transactional
public class TaskCommentService {

	private final TaskRepository tasks;
	private final TaskCommentRepository comments;
	private final UserApi users;
	private final AccessPolicy access;
	private final CurrentUser currentUser;
	private final Clock clock;

	public TaskCommentService(TaskRepository tasks, TaskCommentRepository comments, UserApi users, AccessPolicy access,
			CurrentUser currentUser, Clock clock) {
		this.tasks = tasks;
		this.comments = comments;
		this.users = users;
		this.access = access;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<CommentView> list(Long taskId) {
		requireCanView(load(taskId));
		List<TaskComment> rows = comments.findByTaskIdOrderByCreatedAtAscIdAsc(taskId);
		Map<Long, PersonView> people = people(rows);
		return rows.stream().map(c -> toView(c, people)).toList();
	}

	public CommentView add(Long taskId, String body) {
		requireCanView(load(taskId));
		TaskComment saved = comments.saveAndFlush(TaskComment.create(taskId, currentUser.require().id(), body));
		return toView(saved, people(List.of(saved)));
	}

	public void delete(Long taskId, Long commentId) {
		Task task = load(taskId);
		requireCanView(task);
		TaskComment comment = comments.findByIdAndTaskId(commentId, taskId)
				.orElseThrow(() -> new NotFoundException("Không tìm thấy bình luận " + commentId));
		boolean author = comment.getAuthorId().equals(currentUser.require().id());
		if (!author && !access.isTeamLeader(task.getTeamId())) {
			throw new ForbiddenException("Chỉ tác giả hoặc Leader của nhóm được xoá bình luận");
		}
		comment.markDeleted(clock.instant());
		comments.saveAndFlush(comment);
	}

	private Task load(Long taskId) {
		return tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Không tìm thấy task " + taskId));
	}

	private void requireCanView(Task task) {
		if (!access.canViewTeam(task.getTeamId())) {
			throw new ForbiddenException("Bạn không thuộc nhóm này");
		}
	}

	private Map<Long, PersonView> people(List<TaskComment> rows) {
		var ids = rows.stream().map(TaskComment::getAuthorId).collect(Collectors.toCollection(HashSet::new));
		return users.findSummariesByIds(ids).stream()
				.collect(Collectors.toMap(UserSummary::id, u -> new PersonView(u.id(), u.fullName())));
	}

	private static CommentView toView(TaskComment c, Map<Long, PersonView> people) {
		return new CommentView(c.getId(), people.get(c.getAuthorId()), c.getBody(), c.getCreatedAt());
	}
}
