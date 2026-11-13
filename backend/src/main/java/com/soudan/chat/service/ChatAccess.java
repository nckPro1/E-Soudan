package com.soudan.chat.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.soudan.chat.domain.Conversation;
import com.soudan.chat.domain.DirectStatus;
import com.soudan.chat.repository.ConversationRepository;
import com.soudan.chat.service.ChatViews.PersonView;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.common.security.SystemRole;
import com.soudan.common.security.TeamMembershipLookup;
import com.soudan.team.TeamApi;
import com.soudan.team.TeamInfo;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Quyền trong trò chuyện:
 * <ul>
 * <li>Mọi vai trò đều trò chuyện được, TRỪ Admin (Admin bị chặn ở mọi thao tác).</li>
 * <li>Nhắn riêng: chỉ hai người trong cuộc thấy được cuộc trò chuyện; người ngoài coi như không tồn tại (404).
 * Chỉ nhắn được khi lời mời đã được chấp nhận.</li>
 * <li>Phòng chat nhóm: mọi thành viên của nhóm (Leader, Member, Observer) đọc và viết; nhóm đã lưu trữ thì chỉ đọc.</li>
 * </ul>
 */
@Component
class ChatAccess {

	private final ConversationRepository conversations;
	private final UserApi users;
	private final TeamApi teams;
	private final TeamMembershipLookup memberships;
	private final AccessPolicy policy;
	private final CurrentUser currentUser;

	ChatAccess(ConversationRepository conversations, UserApi users, TeamApi teams, TeamMembershipLookup memberships,
			AccessPolicy policy, CurrentUser currentUser) {
		this.conversations = conversations;
		this.users = users;
		this.teams = teams;
		this.memberships = memberships;
		this.policy = policy;
		this.currentUser = currentUser;
	}

	/** Người đang đăng nhập; Admin bị chặn vì không tham gia trò chuyện. */
	Long me() {
		if (policy.isAdmin()) {
			throw new ForbiddenException(ErrorCode.CHAT_ADMIN_EXCLUDED);
		}
		return currentUser.require().id();
	}

	Conversation load(Long id) {
		return conversations.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy cuộc trò chuyện " + id));
	}

	/** Cuộc trò chuyện mà người này được phép xem. */
	Conversation requireAccess(Long conversationId, Long me) {
		Conversation c = load(conversationId);
		if (c.isDirect()) {
			if (!c.isParticipant(me)) {
				throw new NotFoundException("Không tìm thấy cuộc trò chuyện " + conversationId);
			}
		} else if (memberships.roleOf(c.getTeamId(), me).isEmpty()) {
			throw new ForbiddenException("Bạn không thuộc nhóm này");
		}
		return c;
	}

	/** Có gửi, sửa, xoá, thả biểu cảm được không (khác với chỉ xem). */
	boolean canSend(Conversation c) {
		if (c.isDirect()) {
			if (c.getStatus() != DirectStatus.ACCEPTED) {
				return false;
			}
			return bothUsable(c);
		}
		return teams.findTeam(c.getTeamId()).map(TeamInfo::active).orElse(false);
	}

	void requireCanSend(Conversation c) {
		if (canSend(c)) {
			return;
		}
		if (c.isDirect() && c.getStatus() == DirectStatus.DECLINED) {
			throw new ConflictException(ErrorCode.CHAT_REQUEST_DECLINED);
		}
		if (c.isDirect() && c.getStatus() == DirectStatus.PENDING) {
			throw new ConflictException(ErrorCode.CHAT_NOT_ACCEPTED);
		}
		throw new ConflictException(ErrorCode.CHAT_READ_ONLY);
	}

	/** Những người cần nhận sự kiện thời gian thực của cuộc trò chuyện này (đang hoạt động, không phải Admin). */
	Set<Long> audience(Conversation c) {
		Set<Long> ids = new LinkedHashSet<>();
		if (c.isDirect()) {
			ids.add(c.getUserLowId());
			ids.add(c.getUserHighId());
		} else {
			ids.addAll(teams.findMemberIds(c.getTeamId()));
		}
		Set<Long> usable = new LinkedHashSet<>();
		for (UserSummary u : users.findSummariesByIds(ids)) {
			if (u.active() && u.systemRole() != SystemRole.ADMIN) {
				usable.add(u.id());
			}
		}
		return usable;
	}

	/** Cả hai người của cuộc nhắn riêng còn hoạt động và không phải Admin. */
	private boolean bothUsable(Conversation c) {
		Set<Long> ids = Set.of(c.getUserLowId(), c.getUserHighId());
		long ok = users.findSummariesByIds(ids).stream()
				.filter(u -> u.active() && u.systemRole() != SystemRole.ADMIN).count();
		return ok == 2;
	}

	Map<Long, PersonView> people(Collection<Long> ids) {
		Map<Long, PersonView> result = new HashMap<>();
		if (ids.isEmpty()) {
			return result;
		}
		for (UserSummary u : users.findSummariesByIds(ids)) {
			result.put(u.id(), new PersonView(u.id(), u.fullName(), u.position()));
		}
		return result;
	}

	UserApi users() {
		return users;
	}

	TeamApi teams() {
		return teams;
	}

	TeamMembershipLookup memberships() {
		return memberships;
	}
}
