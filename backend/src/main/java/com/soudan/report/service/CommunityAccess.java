package com.soudan.report.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.report.domain.Community;
import com.soudan.report.repository.CommunityMemberRepository;
import com.soudan.report.repository.CommunityRepository;
import com.soudan.report.service.ReportViews.PersonView;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Quyền trong cộng đồng. Hai vai: CHỦ (Manager tạo ra cộng đồng) và THÀNH VIÊN (Leader/Member được thêm vào).
 * Mọi người khác, kể cả Admin và Manager khác, đều bị từ chối. Leader không có quyền gì hơn Member.
 */
@Component
class CommunityAccess {

	private final CommunityRepository communities;
	private final CommunityMemberRepository members;
	private final UserApi users;
	private final AccessPolicy policy;
	private final CurrentUser currentUser;

	CommunityAccess(CommunityRepository communities, CommunityMemberRepository members, UserApi users,
			AccessPolicy policy, CurrentUser currentUser) {
		this.communities = communities;
		this.members = members;
		this.users = users;
		this.policy = policy;
		this.currentUser = currentUser;
	}

	Long me() {
		return currentUser.require().id();
	}

	Community load(Long id) {
		return communities.findLive(id).orElseThrow(() -> new NotFoundException("Không tìm thấy cộng đồng " + id));
	}

	boolean isOwner(Community community) {
		return policy.isManager() && community.getOwnerId().equals(me());
	}

	boolean isMember(Community community) {
		return !policy.isAdmin() && !policy.isManager()
				&& members.existsByCommunityIdAndUserId(community.getId(), me());
	}

	/** Chủ hoặc thành viên đọc được feed, bình luận, thả biểu cảm. */
	Community requireParticipant(Long communityId) {
		Community community = load(communityId);
		if (!isOwner(community) && !isMember(community)) {
			throw new ForbiddenException(ErrorCode.NOT_COMMUNITY_MEMBER);
		}
		return community;
	}

	/** Chỉ chủ cộng đồng (Manager) sửa cộng đồng và quản lý thành viên. */
	Community requireOwner(Long communityId) {
		Community community = load(communityId);
		if (!isOwner(community)) {
			throw new ForbiddenException("Chỉ chủ cộng đồng được thực hiện thao tác này");
		}
		return community;
	}

	/** Chỉ thành viên được thêm vào mới viết báo cáo (chủ cộng đồng đọc và nhận xét). */
	Community requireMember(Long communityId) {
		Community community = load(communityId);
		if (!isMember(community)) {
			throw new ForbiddenException(ErrorCode.NOT_COMMUNITY_MEMBER);
		}
		return community;
	}

	/** Người có id này đang là thành viên của cộng đồng (không tính chủ cộng đồng) không. */
	boolean hasMember(Long communityId, Long userId) {
		return members.existsByCommunityIdAndUserId(communityId, userId);
	}

	/** Tra tên người cho một tập id (id không còn tồn tại thì không có trong kết quả). */
	Map<Long, PersonView> people(Collection<Long> ids) {
		Map<Long, PersonView> result = new HashMap<>();
		for (UserSummary u : users.findSummariesByIds(ids)) {
			result.put(u.id(), new PersonView(u.id(), u.fullName(), u.position()));
		}
		return result;
	}

	UserApi users() {
		return users;
	}
}
