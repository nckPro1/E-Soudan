package com.soudan.task.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.ai.KnowledgeDoc;
import com.soudan.common.ai.KnowledgeSource;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.task.TaskApi;
import com.soudan.task.domain.Task;
import com.soudan.task.repository.TaskRepository;
import com.soudan.team.TeamApi;
import com.soudan.team.TeamInfo;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Công việc cho trợ lý AI và cho việc nhắc hạn. Người dùng thấy việc của các nhóm mình là thành viên và việc giao cho mình (cùng quy tắc xem
 * task thường); Admin không dùng trợ lý nên không có dữ liệu ở đây.
 */
@Component
@Transactional(readOnly = true)
class TaskKnowledge implements TaskApi, KnowledgeSource {

	private static final int MAX_TEXT = 1500;
	/** Sentinel để mệnh đề IN không rỗng khi người dùng chưa thuộc nhóm nào. */
	private static final List<Long> NO_TEAM = List.of(-1L);

	private final TaskRepository tasks;
	private final TeamApi teams;
	private final UserApi users;
	private final CurrentUser currentUser;
	private final AccessPolicy policy;

	TaskKnowledge(TaskRepository tasks, TeamApi teams, UserApi users, CurrentUser currentUser, AccessPolicy policy) {
		this.tasks = tasks;
		this.teams = teams;
		this.users = users;
		this.currentUser = currentUser;
		this.policy = policy;
	}

	@Override
	public String kind() {
		return "TASK";
	}

	@Override
	public List<KnowledgeDoc> myOpenTasks(int limit) {
		return docs(tasks.openOf(currentUser.require().id(), PageRequest.of(0, limit)));
	}

	@Override
	public List<DueTask> openTasksDueBetween(java.time.LocalDate from, java.time.LocalDate to) {
		return tasks.openDueBetween(from, to).stream()
				.map(t -> new DueTask(t.getId(), t.getTeamId(), t.getTitle(), t.getAssigneeId(), t.getDueDate())).toList();
	}

	@Override
	public List<KnowledgeDoc> myTasksChangedSince(Instant since, int limit) {
		return docs(tasks.changedSince(currentUser.require().id(), since, PageRequest.of(0, limit)));
	}

	@Override
	public List<KnowledgeDoc> visibleSince(Instant since, int limit) {
		if (policy.isAdmin()) {
			return List.of();
		}
		Long me = currentUser.require().id();
		List<Long> teamIds = teams.findTeamIdsOf(me);
		return docs(tasks.visibleSince(teamIds.isEmpty() ? NO_TEAM : teamIds, me, since, PageRequest.of(0, limit)));
	}

	private List<KnowledgeDoc> docs(List<Task> rows) {
		Set<Long> userIds = new HashSet<>();
		Set<Long> teamIds = new HashSet<>();
		for (Task t : rows) {
			userIds.add(t.getAssigneeId());
			teamIds.add(t.getTeamId());
		}
		Map<Long, String> people = userIds.isEmpty() ? Map.of()
				: users.findSummariesByIds(userIds).stream().collect(Collectors.toMap(UserSummary::id, UserSummary::fullName));
		Map<Long, String> teamNames = teamIds.isEmpty() ? Map.of()
				: teams.findTeams(teamIds).stream().collect(Collectors.toMap(TeamInfo::id, TeamInfo::name));
		List<KnowledgeDoc> result = new ArrayList<>();
		for (Task t : rows) {
			StringBuilder text = new StringBuilder();
			text.append("Nhóm: ").append(teamNames.getOrDefault(t.getTeamId(), "")).append(". ");
			text.append("Trạng thái: ").append(t.getStatus()).append(". ");
			text.append("Độ ưu tiên: ").append(t.getPriority()).append(". ");
			text.append("Hạn: ").append(t.getDueDate() == null ? "không có" : t.getDueDate().toString()).append(". ");
			text.append("Người phụ trách: ").append(people.getOrDefault(t.getAssigneeId(), "")).append(".");
			if (t.getRevisionReason() != null) {
				text.append(" Lý do trả lại: ").append(t.getRevisionReason()).append(".");
			}
			if (t.getDescription() != null) {
				text.append(" Mô tả: ").append(t.getDescription());
			}
			String body = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "…" : text.toString();
			result.add(new KnowledgeDoc("TASK", t.getId(), "Công việc: " + t.getTitle(), body, t.getUpdatedAt(),
					people.get(t.getAssigneeId()), "/tasks/" + t.getId()));
		}
		return result;
	}
}
