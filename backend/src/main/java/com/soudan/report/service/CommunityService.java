package com.soudan.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.SystemRole;
import com.soudan.report.domain.Community;
import com.soudan.report.domain.CommunityMember;
import com.soudan.report.repository.CommunityMemberRepository;
import com.soudan.report.repository.CommunityMemberRepository.MemberCount;
import com.soudan.report.repository.CommunityRepository;
import com.soudan.report.repository.ReportPostRepository;
import com.soudan.report.service.ReportViews.CommunityDetail;
import com.soudan.report.service.ReportViews.CommunitySummary;
import com.soudan.report.service.ReportViews.PendingView;
import com.soudan.report.service.ReportViews.PersonView;
import com.soudan.user.UserSummary;

/**
 * Cộng đồng báo cáo: Manager tạo, đổi tên, xoá và thêm/bớt thành viên (Leader và Member). Người được thêm mới
 * thấy trang báo cáo của cộng đồng. Danh sách "chưa nộp" chỉ dành cho chủ cộng đồng.
 */
@Service
@Transactional
public class CommunityService {

	private final CommunityRepository communities;
	private final CommunityMemberRepository members;
	private final ReportPostRepository posts;
	private final CommunityAccess access;
	private final AccessPolicy policy;
	private final Clock clock;
	private final ZoneId zone;

	public CommunityService(CommunityRepository communities, CommunityMemberRepository members,
			ReportPostRepository posts, CommunityAccess access, AccessPolicy policy, Clock clock,
			@Value("${app.timezone}") String timezone) {
		this.communities = communities;
		this.members = members;
		this.posts = posts;
		this.access = access;
		this.policy = policy;
		this.clock = clock;
		this.zone = ZoneId.of(timezone);
	}

	/** Manager thấy cộng đồng mình tạo; Leader/Member thấy cộng đồng mình được thêm vào; Admin không có cộng đồng. */
	@Transactional(readOnly = true)
	public List<CommunitySummary> listMine() {
		Long me = access.me();
		List<Community> rows;
		if (policy.isManager()) {
			rows = communities.findOwnedBy(me);
		} else if (policy.isAdmin()) {
			rows = List.of();
		} else {
			rows = communities.findJoinedBy(me);
		}
		return summaries(rows);
	}

	public CommunityDetail create(String name, String description) {
		if (!policy.isManager()) {
			throw new ForbiddenException("Chỉ Manager được tạo cộng đồng");
		}
		Community saved = communities.saveAndFlush(Community.create(name, description, access.me()));
		return detail(saved);
	}

	@Transactional(readOnly = true)
	public CommunityDetail get(Long id) {
		return detail(access.requireParticipant(id));
	}

	public CommunityDetail update(Long id, String name, String description) {
		Community community = access.requireOwner(id);
		community.rename(name, description);
		communities.saveAndFlush(community);
		return detail(community);
	}

	public void delete(Long id) {
		Community community = access.requireOwner(id);
		community.markDeleted(clock.instant());
		communities.saveAndFlush(community);
	}

	/** Thêm nhiều người một lượt. Chỉ nhận tài khoản USER đang hoạt động; người đã có trong cộng đồng bị báo lỗi. */
	public CommunityDetail addMembers(Long id, List<Long> userIds) {
		Community community = access.requireOwner(id);
		if (userIds == null || userIds.isEmpty()) {
			throw new ValidationException("Chưa chọn người nào để thêm");
		}
		Set<Long> unique = new HashSet<>(userIds);
		Map<Long, UserSummary> found = access.users().findSummariesByIds(unique).stream()
				.collect(Collectors.toMap(UserSummary::id, u -> u));
		for (Long userId : unique) {
			UserSummary user = found.get(userId);
			if (user == null || !user.active() || user.systemRole() != SystemRole.USER) {
				throw new ValidationException(ErrorCode.INVALID_COMMUNITY_MEMBER,
						ErrorCode.INVALID_COMMUNITY_MEMBER.defaultMessage());
			}
			if (members.existsByCommunityIdAndUserId(id, userId)) {
				throw new ConflictException(ErrorCode.ALREADY_COMMUNITY_MEMBER);
			}
		}
		try {
			for (Long userId : unique) {
				members.save(new CommunityMember(id, userId, clock.instant()));
			}
			members.flush();
		} catch (DataIntegrityViolationException e) {
			throw new ConflictException(ErrorCode.ALREADY_COMMUNITY_MEMBER);
		}
		return detail(community);
	}

	/** Bớt thành viên. Báo cáo cũ của họ vẫn còn trong feed. */
	public CommunityDetail removeMember(Long id, Long userId) {
		Community community = access.requireOwner(id);
		if (!members.existsByCommunityIdAndUserId(id, userId)) {
			throw new NotFoundException("Người này không có trong cộng đồng");
		}
		members.deleteByCommunityIdAndUserId(id, userId);
		members.flush();
		return detail(community);
	}

	/** Thành viên chưa nộp báo cáo trong ngày (mặc định hôm nay). Chỉ chủ cộng đồng xem. */
	@Transactional(readOnly = true)
	public PendingView pending(Long id, LocalDate date) {
		access.requireOwner(id);
		LocalDate day = date != null ? date : today();
		Set<Long> submitted = new HashSet<>(posts.authorsOfDay(id, day));
		List<Long> missingIds = members.findByCommunityIdOrderByAddedAtAscIdAsc(id).stream()
				.map(CommunityMember::getUserId)
				.filter(userId -> !submitted.contains(userId))
				.toList();
		Map<Long, PersonView> people = access.people(missingIds);
		return new PendingView(day, missingIds.stream().map(people::get).filter(p -> p != null).toList());
	}

	LocalDate today() {
		return LocalDate.now(clock.withZone(zone));
	}

	// ---- nội bộ ----

	private CommunityDetail detail(Community community) {
		List<Long> memberIds = members.findByCommunityIdOrderByAddedAtAscIdAsc(community.getId()).stream()
				.map(CommunityMember::getUserId).toList();
		Map<Long, PersonView> people = access.people(memberIds);
		List<PersonView> memberViews = new ArrayList<>();
		memberIds.forEach(userId -> {
			PersonView p = people.get(userId);
			if (p != null) {
				memberViews.add(p);
			}
		});
		CommunitySummary summary = summaries(List.of(community)).get(0);
		return new CommunityDetail(summary, memberViews, community.getVersion());
	}

	private List<CommunitySummary> summaries(List<Community> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> ids = rows.stream().map(Community::getId).toList();
		Map<Long, Long> counts = members.countByCommunities(ids).stream()
				.collect(Collectors.toMap(MemberCount::getCommunityId, MemberCount::getTotal));
		Map<Long, PersonView> owners = access.people(rows.stream().map(Community::getOwnerId).distinct().toList());
		Long me = access.me();
		return rows.stream()
				.map(c -> new CommunitySummary(c.getId(), c.getName(), c.getDescription(), owners.get(c.getOwnerId()),
						counts.getOrDefault(c.getId(), 0L).intValue(), c.getOwnerId().equals(me) && policy.isManager()))
				.toList();
	}
}
