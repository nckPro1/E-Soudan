package com.soudan.team.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.api.PageQuery;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.search.SearchSpecification;
import com.soudan.common.search.SearchWhitelist;
import com.soudan.common.search.SearchWhitelist.FilterRule;
import com.soudan.common.security.AuthenticatedUser;
import com.soudan.common.security.CurrentUser;
import com.soudan.common.security.SystemRole;
import com.soudan.common.security.TeamRole;
import com.soudan.team.domain.Team;
import com.soudan.team.domain.TeamMember;
import com.soudan.team.domain.TeamStatus;
import com.soudan.team.repository.TeamRepository;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Nghiệp vụ nhóm. Mỗi phương thức là một giao dịch. Quy tắc theo dữ liệu nhóm nằm ở {@link Team};
 * ở đây là quy tắc cần biết người dùng (ai được làm Leader/Member/Observer) và tên nhóm không trùng.
 * Người dùng được tra qua {@link UserApi}, không đụng repository của module user.
 */
@Service
@Transactional
public class TeamService {

	static final SearchWhitelist WHITELIST = new SearchWhitelist(
			Map.of(
					"q", FilterRule.contains("name", "description"),
					"status", FilterRule.equalsTo("status")),
			Set.of("name", "createdAt"));

	private final TeamRepository teams;
	private final UserApi users;
	private final CurrentUser currentUser;
	private final Clock clock;

	public TeamService(TeamRepository teams, UserApi users, CurrentUser currentUser, Clock clock) {
		this.teams = teams;
		this.users = users;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	public TeamDetail create(CreateTeamCommand command) {
		List<Long> leaders = nullToEmpty(command.leaderIds());
		List<Long> members = nullToEmpty(command.memberIds());
		List<Long> observers = nullToEmpty(command.observerIds());
		if (leaders.isEmpty()) {
			throw new ValidationException("Nhóm phải có ít nhất một Leader");
		}
		Set<Long> all = new HashSet<>();
		for (Long id : Stream.of(leaders, members, observers).flatMap(List::stream).toList()) {
			if (!all.add(id)) {
				throw new ValidationException(ErrorCode.INVALID_TEAM_MEMBER, "Một người chỉ được xuất hiện một lần trong nhóm");
			}
		}
		assertNameFree(command.name().trim(), null);
		Map<Long, UserSummary> found = loadUsers(all);

		Team team = Team.create(command.name(), command.description());
		add(team, leaders, TeamRole.LEADER, found);
		add(team, members, TeamRole.MEMBER, found);
		add(team, observers, TeamRole.OBSERVER, found);
		teams.saveAndFlush(team);
		return detail(team);
	}

	public TeamDetail update(Long id, long version, String name, String description) {
		Team team = loadForUpdate(id, version);
		if (!team.isArchived()) {
			assertNameFree(name.trim(), id);
		}
		team.updateInfo(name, description);
		teams.saveAndFlush(team);
		return detail(team);
	}

	public TeamDetail archive(Long id, long version) {
		Team team = loadForUpdate(id, version);
		team.archive();
		teams.saveAndFlush(team);
		return detail(team);
	}

	public TeamDetail addMember(Long teamId, Long userId, TeamRole role) {
		Team team = loadForUpdate(teamId);
		UserSummary user = loadUsers(Set.of(userId)).get(userId);
		assertCanHold(user, role);
		team.addMember(userId, role, clock.instant());
		teams.saveAndFlush(team);
		return detail(team);
	}

	public TeamDetail changeMemberRole(Long teamId, Long userId, TeamRole role) {
		Team team = loadForUpdate(teamId);
		UserSummary user = loadUsers(Set.of(userId)).get(userId);
		assertCanHold(user, role);
		team.changeMemberRole(userId, role);
		teams.saveAndFlush(team);
		return detail(team);
	}

	public TeamDetail removeMember(Long teamId, Long userId) {
		Team team = loadForUpdate(teamId);
		team.removeMember(userId);
		teams.saveAndFlush(team);
		return detail(team);
	}

	@Transactional(readOnly = true)
	public TeamDetail get(Long id) {
		return detail(teams.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy nhóm " + id)));
	}

	/**
	 * Danh sách nhóm. Mặc định chỉ nhóm đang hoạt động. Admin thấy tất cả; người khác chỉ thấy nhóm mình có mặt.
	 */
	@Transactional(readOnly = true)
	public Page<TeamSummary> search(PageQuery query) {
		Map<String, String> filters = new HashMap<>(query.filters());
		filters.putIfAbsent("status", TeamStatus.ACTIVE.name());
		Specification<Team> spec = SearchSpecification.of(filters, WHITELIST);
		AuthenticatedUser viewer = currentUser.require();
		if (viewer.role() != SystemRole.ADMIN) {
			spec = spec.and(involving(viewer.id()));
		}
		return teams.findAll(spec, SearchSpecification.pageable(query, WHITELIST, Sort.by("name")))
				.map(t -> new TeamSummary(t.getId(), t.getName(), t.getDescription(), t.getStatus(),
						t.getMembers().size(), t.getVersion(), t.getCreatedAt()));
	}

	private static Specification<Team> involving(Long userId) {
		return (root, query, cb) -> {
			Subquery<Long> sub = query.subquery(Long.class);
			Root<TeamMember> member = sub.from(TeamMember.class);
			sub.select(member.get("id")).where(
					cb.equal(member.get("team"), root),
					cb.equal(member.get("userId"), userId));
			return cb.exists(sub);
		};
	}

	private void add(Team team, List<Long> ids, TeamRole role, Map<Long, UserSummary> found) {
		for (Long id : ids) {
			assertCanHold(found.get(id), role);
			team.addMember(id, role, clock.instant());
		}
	}

	/** Leader/Member phải là USER, Observer phải là MANAGER; Admin không tham gia nhóm; tài khoản phải đang hoạt động. */
	private static void assertCanHold(UserSummary user, TeamRole role) {
		if (!user.active()) {
			throw new ValidationException(ErrorCode.INVALID_TEAM_MEMBER, "Tài khoản " + user.fullName() + " đang bị khoá");
		}
		SystemRole required = role == TeamRole.OBSERVER ? SystemRole.MANAGER : SystemRole.USER;
		if (user.systemRole() != required) {
			throw new ValidationException(ErrorCode.INVALID_TEAM_MEMBER,
					user.fullName() + " (" + user.systemRole() + ") không thể giữ vai trò " + role);
		}
	}

	private Map<Long, UserSummary> loadUsers(Set<Long> ids) {
		Map<Long, UserSummary> found = users.findSummariesByIds(ids).stream()
				.collect(Collectors.toMap(UserSummary::id, Function.identity()));
		for (Long id : ids) {
			if (!found.containsKey(id)) {
				throw new ValidationException(ErrorCode.INVALID_TEAM_MEMBER, "Không tìm thấy tài khoản " + id);
			}
		}
		return found;
	}

	private void assertNameFree(String name, Long exceptId) {
		boolean taken = exceptId == null
				? teams.existsByNameIgnoreCaseAndStatus(name, TeamStatus.ACTIVE)
				: teams.existsByNameIgnoreCaseAndStatusAndIdNot(name, TeamStatus.ACTIVE, exceptId);
		if (taken) {
			throw new ConflictException(ErrorCode.TEAM_NAME_ALREADY_EXISTS);
		}
	}

	private Team loadForUpdate(Long id) {
		return teams.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy nhóm " + id));
	}

	private Team loadForUpdate(Long id, long version) {
		Team team = loadForUpdate(id);
		if (team.getVersion() != version) {
			throw new ConflictException(ErrorCode.VERSION_CONFLICT);
		}
		return team;
	}

	private TeamDetail detail(Team team) {
		Set<Long> ids = team.getMembers().stream().map(TeamMember::getUserId).collect(Collectors.toSet());
		Map<Long, UserSummary> names = users.findSummariesByIds(ids).stream()
				.collect(Collectors.toMap(UserSummary::id, Function.identity()));
		List<TeamDetail.MemberDetail> members = new ArrayList<>();
		for (TeamMember m : team.getMembers()) {
			UserSummary u = names.get(m.getUserId());
			members.add(new TeamDetail.MemberDetail(m.getUserId(), u == null ? "?" : u.fullName(),
					u == null ? null : u.position(), m.getTeamRole(), m.getJoinedAt()));
		}
		members.sort(Comparator.comparing((TeamDetail.MemberDetail m) -> m.teamRole().ordinal())
				.thenComparing(TeamDetail.MemberDetail::fullName));
		return new TeamDetail(team.getId(), team.getName(), team.getDescription(), team.getStatus(), team.getVersion(),
				team.getCreatedAt(), members);
	}

	private static List<Long> nullToEmpty(List<Long> ids) {
		return ids == null ? List.of() : ids;
	}
}
