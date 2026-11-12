package com.soudan.task.service;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.api.PageQuery;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.search.SearchSpecification;
import com.soudan.common.search.SearchWhitelist;
import com.soudan.common.search.SearchWhitelist.FilterRule;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.common.security.TeamMembershipLookup;
import com.soudan.common.security.TeamRole;
import com.soudan.task.TaskAssignedEvent;
import com.soudan.task.TaskSubmittedForReviewEvent;
import com.soudan.task.domain.Task;
import com.soudan.task.domain.TaskHistory;
import com.soudan.task.domain.TaskStatus;
import com.soudan.task.domain.TaskSubmission;
import com.soudan.task.repository.TaskHistoryRepository;
import com.soudan.task.repository.TaskRepository;
import com.soudan.task.repository.TaskSubmissionRepository;
import com.soudan.task.service.TaskViews.HistoryView;
import com.soudan.task.service.TaskViews.PersonView;
import com.soudan.task.service.TaskViews.SubmissionView;
import com.soudan.task.service.TaskViews.TaskView;
import com.soudan.team.TeamApi;
import com.soudan.team.TeamInfo;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Nghiệp vụ task. Mỗi phương thức là một giao dịch; mỗi lần đổi trạng thái ghi lịch sử trong cùng giao dịch.
 * Quy tắc "từ trạng thái nào sang trạng thái nào" ở {@link Task}; ở đây là "ai được làm" và các ràng buộc
 * cần dữ liệu ngoài (người phụ trách phải là Member của nhóm, nhóm phải đang hoạt động).
 */
@Service
@Transactional
public class TaskService {

	static final SearchWhitelist WHITELIST = new SearchWhitelist(
			Map.of(
					"q", FilterRule.contains("title", "description"),
					"status", FilterRule.equalsTo("status"),
					"assigneeId", FilterRule.equalsTo("assigneeId"),
					"priority", FilterRule.equalsTo("priority"),
					"dueFrom", FilterRule.atLeast("dueDate"),
					"dueTo", FilterRule.atMost("dueDate")),
			Set.of("dueDate", "createdAt", "priority", "status", "title"));

	private final TaskRepository tasks;
	private final TaskHistoryRepository histories;
	private final TaskSubmissionRepository submissions;
	private final TeamApi teams;
	private final UserApi users;
	private final TeamMembershipLookup memberships;
	private final AccessPolicy access;
	private final CurrentUser currentUser;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public TaskService(TaskRepository tasks, TaskHistoryRepository histories, TaskSubmissionRepository submissions,
			TeamApi teams, UserApi users, TeamMembershipLookup memberships, AccessPolicy access,
			CurrentUser currentUser, Clock clock, ApplicationEventPublisher events) {
		this.tasks = tasks;
		this.histories = histories;
		this.submissions = submissions;
		this.teams = teams;
		this.users = users;
		this.memberships = memberships;
		this.access = access;
		this.currentUser = currentUser;
		this.clock = clock;
		this.events = events;
	}

	// ---- ghi ----

	/** Leader của nhóm giao task cho một Member của chính nhóm đó; nhóm phải đang hoạt động. */
	public TaskView create(Long teamId, TaskCommand command) {
		TeamInfo team = teams.findTeam(teamId).orElseThrow(() -> new NotFoundException("Không tìm thấy nhóm " + teamId));
		requireLeader(teamId);
		if (!team.active()) {
			throw new ConflictException(ErrorCode.TEAM_ARCHIVED);
		}
		requireAssigneeIsMember(teamId, command.assigneeId());

		Long me = currentUser.require().id();
		Task task = Task.create(teamId, command.title(), command.description(), command.assigneeId(), me,
				command.dueDate(), command.priority(), command.requireFile());
		tasks.saveAndFlush(task);
		record(task, null, null);
		publishAssigned(task);
		return view(task);
	}

	/** Leader sửa thông tin task (chưa DONE). Không đổi được trạng thái ở đây. */
	public TaskView update(Long id, long version, TaskCommand command) {
		Task task = load(id);
		requireLeader(task.getTeamId());
		checkVersion(task, version);
		requireAssigneeIsMember(task.getTeamId(), command.assigneeId());
		Long previousAssignee = task.getAssigneeId();
		task.update(command.title(), command.description(), command.assigneeId(), command.dueDate(),
				command.priority(), command.requireFile());
		tasks.saveAndFlush(task);
		if (!previousAssignee.equals(task.getAssigneeId())) {
			publishAssigned(task);
		}
		return view(task);
	}

	public TaskView start(Long id, long version) {
		Task task = load(id);
		requireAssignee(task);
		checkVersion(task, version);
		return transition(task, task::start, null);
	}

	public TaskView submitForReview(Long id, long version) {
		Task task = load(id);
		requireAssignee(task);
		checkVersion(task, version);
		boolean hasFile = submissions.existsByTaskId(id);
		TaskView result = transition(task, () -> task.submitForReview(hasFile), null);
		events.publishEvent(new TaskSubmittedForReviewEvent(task.getId(), task.getTeamId(), task.getTitle(),
				task.getAssigneeId()));
		return result;
	}

	public TaskView approve(Long id, long version) {
		Task task = load(id);
		requireLeader(task.getTeamId());
		checkVersion(task, version);
		return transition(task, task::approve, null);
	}

	public TaskView requestRevision(Long id, long version, String reason) {
		Task task = load(id);
		requireLeader(task.getTeamId());
		checkVersion(task, version);
		return transition(task, () -> task.requestRevision(reason), reason == null ? null : reason.trim());
	}

	// ---- đọc ----

	@Transactional(readOnly = true)
	public TaskView get(Long id) {
		Task task = load(id);
		requireCanView(task.getTeamId());
		return view(task);
	}

	@Transactional(readOnly = true)
	public List<HistoryView> history(Long id) {
		Task task = load(id);
		requireCanView(task.getTeamId());
		List<TaskHistory> rows = histories.findByTaskIdOrderByChangedAtAscIdAsc(id);
		Map<Long, PersonView> people = people(rows.stream().map(TaskHistory::getChangedBy).collect(Collectors.toSet()));
		return rows.stream()
				.map(h -> new HistoryView(h.getId(), h.getFromStatus(), h.getToStatus(), people.get(h.getChangedBy()),
						h.getChangedAt(), h.getNote()))
				.toList();
	}

	/** Task của một nhóm. Người xem phải có mặt trong nhóm (hoặc là Admin). */
	@Transactional(readOnly = true)
	public Page<TaskView> searchByTeam(Long teamId, PageQuery query) {
		requireCanView(teamId);
		Specification<Task> spec = SearchSpecification.<Task>of(query.filters(), WHITELIST)
				.and((root, q, cb) -> cb.equal(root.get("teamId"), teamId));
		return page(spec, query);
	}

	/** "Việc của tôi": task giao cho người đang đăng nhập, ở mọi nhóm. */
	@Transactional(readOnly = true)
	public Page<TaskView> searchMine(PageQuery query) {
		Long me = currentUser.require().id();
		Map<String, String> filters = new HashMap<>(query.filters());
		filters.remove("assigneeId");
		Specification<Task> spec = SearchSpecification.<Task>of(filters, WHITELIST)
				.and((root, q, cb) -> cb.equal(root.get("assigneeId"), me));
		return page(spec, query);
	}

	// ---- nội bộ ----

	private Page<TaskView> page(Specification<Task> spec, PageQuery query) {
		Page<Task> page = tasks.findAll(spec,
				SearchSpecification.pageable(query, WHITELIST, Sort.by(Sort.Order.asc("dueDate").nullsLast(),
						Sort.Order.desc("createdAt"))));
		Map<Long, TeamInfo> teamNames = teamsOf(page.getContent());
		Map<Long, PersonView> people = people(page.getContent().stream()
				.flatMap(t -> Stream.of(t.getAssigneeId(), t.getAssignedBy()))
				.collect(Collectors.toSet()));
		return page.map(t -> toView(t, teamNames.get(t.getTeamId()), people, List.of()));
	}

	/** Chạy chuyển trạng thái của domain, lưu và ghi lịch sử trong cùng giao dịch. */
	private TaskView transition(Task task, Runnable change, String note) {
		TaskStatus from = task.getStatus();
		change.run();
		tasks.saveAndFlush(task);
		record(task, from, note);
		return view(task);
	}

	/** Báo cho module khác (email...). Sự kiện chỉ được xử lý sau khi giao dịch này commit thành công. */
	private void publishAssigned(Task task) {
		events.publishEvent(new TaskAssignedEvent(task.getId(), task.getTeamId(), task.getTitle(),
				task.getAssigneeId(), task.getAssignedBy(), task.getDueDate()));
	}

	private void record(Task task, TaskStatus from, String note) {
		histories.save(new TaskHistory(task.getId(), from, task.getStatus(), currentUser.require().id(),
				clock.instant(), note));
	}

	private Task load(Long id) {
		return tasks.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy task " + id));
	}

	private static void checkVersion(Task task, long version) {
		if (task.getVersion() != version) {
			throw new ConflictException(ErrorCode.VERSION_CONFLICT);
		}
	}

	private void requireLeader(Long teamId) {
		if (!access.isTeamLeader(teamId)) {
			throw new ForbiddenException("Chỉ Leader của nhóm được thực hiện thao tác này");
		}
	}

	private void requireAssignee(Task task) {
		if (!task.getAssigneeId().equals(currentUser.require().id())) {
			throw new ForbiddenException("Chỉ người được giao việc mới thực hiện được thao tác này");
		}
	}

	private void requireCanView(Long teamId) {
		if (!access.canViewTeam(teamId)) {
			throw new ForbiddenException("Bạn không thuộc nhóm này");
		}
	}

	private void requireAssigneeIsMember(Long teamId, Long assigneeId) {
		TeamRole role = memberships.roleOf(teamId, assigneeId).orElse(null);
		if (role != TeamRole.MEMBER) {
			throw new ValidationException(ErrorCode.ASSIGNEE_NOT_IN_TEAM,
					"Người phụ trách phải là Member của nhóm");
		}
	}

	private TaskView view(Task task) {
		Map<Long, PersonView> people = people(Set.of(task.getAssigneeId(), task.getAssignedBy()));
		List<TaskSubmission> files = submissions.findByTaskIdOrderByUploadedAtAscIdAsc(task.getId());
		Map<Long, TeamInfo> team = teamsOf(List.of(task));
		return toView(task, team.get(task.getTeamId()), people, files.stream()
				.map(f -> new SubmissionView(f.getId(), f.getFileName(), f.getContentType(), f.getSizeBytes(),
						f.getUploadedBy(), f.getUploadedAt()))
				.toList());
	}

	private static TaskView toView(Task t, TeamInfo team, Map<Long, PersonView> people, List<SubmissionView> files) {
		return new TaskView(t.getId(), t.getTeamId(), team == null ? null : team.name(), t.getTitle(),
				t.getDescription(), people.get(t.getAssigneeId()), people.get(t.getAssignedBy()), t.getDueDate(),
				t.getPriority(), t.getStatus(), t.isRequireFile(), t.getRevisionReason(), t.getVersion(),
				t.getCreatedAt(), files);
	}

	private Map<Long, TeamInfo> teamsOf(Collection<Task> list) {
		Set<Long> ids = list.stream().map(Task::getTeamId).collect(Collectors.toSet());
		return teams.findTeams(ids).stream().collect(Collectors.toMap(TeamInfo::id, Function.identity()));
	}

	private Map<Long, PersonView> people(Set<Long> ids) {
		Map<Long, PersonView> result = new HashMap<>();
		for (UserSummary u : users.findSummariesByIds(new HashSet<>(ids))) {
			result.put(u.id(), new PersonView(u.id(), u.fullName()));
		}
		return result;
	}
}
