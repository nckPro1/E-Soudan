package com.soudan.chat.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.chat.ChatRequestAcceptedEvent;
import com.soudan.chat.ChatRequestedEvent;
import com.soudan.chat.domain.ChatAttachment;
import com.soudan.chat.domain.ChatMessage;
import com.soudan.chat.domain.Conversation;
import com.soudan.chat.domain.DirectStatus;
import com.soudan.chat.repository.ChatAttachmentRepository;
import com.soudan.chat.repository.ChatMessageRepository;
import com.soudan.chat.repository.ChatMessageRepository.UnreadCount;
import com.soudan.chat.repository.ConversationRepository;
import com.soudan.chat.service.ChatViews.ConversationView;
import com.soudan.chat.service.ChatViews.LastMessage;
import com.soudan.chat.service.ChatViews.PersonView;
import com.soudan.chat.service.ChatViews.TeamRef;
import com.soudan.chat.service.ChatViews.UnreadEntry;
import com.soudan.chat.service.ChatViews.UnreadSummary;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.security.SystemRole;
import com.soudan.team.TeamInfo;
import com.soudan.user.UserSummary;

/**
 * Cuộc trò chuyện: nhắn riêng và phòng chat nhóm.
 * <p>
 * Nhắn riêng bắt đầu bằng LỜI MỜI: A bấm "Nhắn tin" ở hồ sơ B thì tạo lời mời; B chấp nhận thì cả hai nhắn được, B từ
 * chối thì thôi. A không mời lại được sau khi bị từ chối (tránh quấy rối), nhưng B đổi ý thì có thể chủ động mời A.
 * Nếu B đã mời A từ trước mà A bấm "Nhắn tin" cho B thì coi như A đồng ý và chấp nhận luôn.
 * Phòng chat nhóm là của nhóm: mỗi nhóm một phòng, thành viên là thành viên nhóm.
 */
@Service
@Transactional
public class ConversationService {

	private final ConversationRepository conversations;
	private final ChatMessageRepository messages;
	private final ChatAttachmentRepository attachments;
	private final ChatAccess access;
	private final ChatReadState readState;
	private final ChatPush push;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public ConversationService(ConversationRepository conversations, ChatMessageRepository messages,
			ChatAttachmentRepository attachments, ChatAccess access, ChatReadState readState, ChatPush push,
			ApplicationEventPublisher events, Clock clock) {
		this.conversations = conversations;
		this.messages = messages;
		this.attachments = attachments;
		this.access = access;
		this.readState = readState;
		this.push = push;
		this.events = events;
		this.clock = clock;
	}

	/** Các cuộc nhắn riêng của mình (cả lời mời đang chờ), hoạt động gần nhất trước. Phòng chat nhóm nằm ở app Nhóm. */
	@Transactional(readOnly = true)
	public List<ConversationView> listDirect() {
		Long me = access.me();
		return views(conversations.findDirectsOf(me), me);
	}

	@Transactional(readOnly = true)
	public ConversationView get(Long id) {
		Long me = access.me();
		return views(List.of(access.requireAccess(id, me)), me).get(0);
	}

	/** Cuộc nhắn riêng với một người (mọi trạng thái), hoặc rỗng nếu chưa từng mời nhau. Dùng cho nút ở hồ sơ. */
	@Transactional(readOnly = true)
	public Optional<ConversationView> directWith(Long userId) {
		Long me = access.me();
		return conversations.findDirectBetween(Math.min(me, userId), Math.max(me, userId))
				.map(c -> views(List.of(c), me).get(0));
	}

	/** Bấm "Nhắn tin" ở hồ sơ một người. Xem mô tả ở đầu lớp về các trường hợp. */
	public ConversationView startDirect(Long userId) {
		Long me = access.me();
		if (me.equals(userId)) {
			throw new ValidationException(ErrorCode.INVALID_CHAT_TARGET, "Không thể nhắn tin cho chính mình");
		}
		UserSummary target = access.users().findSummariesByIds(Set.of(userId)).stream().findFirst().orElse(null);
		if (target == null || !target.active() || target.systemRole() == SystemRole.ADMIN) {
			throw new ValidationException(ErrorCode.INVALID_CHAT_TARGET, ErrorCode.INVALID_CHAT_TARGET.defaultMessage());
		}
		long low = Math.min(me, userId);
		long high = Math.max(me, userId);
		Conversation existing = conversations.findDirectBetween(low, high).orElse(null);
		if (existing == null) {
			try {
				Conversation created = conversations.saveAndFlush(Conversation.request(me, userId, clock.instant()));
				requested(created, me, userId);
				return views(List.of(created), me).get(0);
			} catch (DataIntegrityViolationException e) {
				// Hai bên bấm cùng lúc: cuộc trò chuyện đã được người kia tạo, xử lý như trường hợp đã tồn tại
				existing = conversations.findDirectBetween(low, high).orElseThrow(() -> e);
			}
		}
		switch (existing.getStatus()) {
			case ACCEPTED -> {
				// đã nhắn được với nhau
			}
			case PENDING -> {
				if (!me.equals(existing.getRequestedBy())) {
					// Họ đã mời mình từ trước, mình cũng muốn nhắn: coi như chấp nhận
					acceptInternal(existing, me);
				}
			}
			case DECLINED -> {
				if (me.equals(existing.getRequestedBy())) {
					throw new ConflictException(ErrorCode.CHAT_REQUEST_DECLINED);
				}
				existing.reopenFrom(me, clock.instant());
				conversations.saveAndFlush(existing);
				requested(existing, me, userId);
			}
		}
		return views(List.of(existing), me).get(0);
	}

	/** Bên nhận chấp nhận lời mời. */
	public ConversationView accept(Long id) {
		Long me = access.me();
		Conversation c = access.requireAccess(id, me);
		requireRecipient(c, me);
		acceptInternal(c, me);
		return views(List.of(c), me).get(0);
	}

	/** Bên nhận từ chối lời mời. Cuộc trò chuyện biến khỏi danh sách của cả hai. */
	public ConversationView decline(Long id) {
		Long me = access.me();
		Conversation c = access.requireAccess(id, me);
		requireRecipient(c, me);
		c.decline(clock.instant());
		conversations.saveAndFlush(c);
		push.conversation(List.of(c.getUserLowId(), c.getUserHighId()), c.getId());
		return views(List.of(c), me).get(0);
	}

	/** Bên gửi thu hồi lời mời đang chờ. Chưa có tin nhắn nào nên xoá hẳn dòng lời mời. */
	public void cancel(Long id) {
		Long me = access.me();
		Conversation c = access.requireAccess(id, me);
		if (!c.isDirect() || c.getStatus() != DirectStatus.PENDING || !me.equals(c.getRequestedBy())) {
			throw new ForbiddenException("Chỉ người gửi mới thu hồi được lời mời đang chờ");
		}
		conversations.delete(c);
		conversations.flush();
		push.conversationRemoved(List.of(c.getUserLowId(), c.getUserHighId()), c.getId());
	}

	/** Phòng chat của một nhóm (tạo ở lần đầu mở). Mọi thành viên nhóm, kể cả Observer, vào được. */
	public ConversationView teamConversation(Long teamId) {
		Long me = access.me();
		if (access.memberships().roleOf(teamId, me).isEmpty()) {
			throw new ForbiddenException("Bạn không thuộc nhóm này");
		}
		if (access.teams().findTeam(teamId).isEmpty()) {
			throw new NotFoundException("Không tìm thấy nhóm " + teamId);
		}
		Conversation c = conversations.findByTeamId(teamId).orElse(null);
		if (c == null) {
			try {
				c = conversations.saveAndFlush(Conversation.forTeam(teamId, clock.instant()));
			} catch (DataIntegrityViolationException e) {
				c = conversations.findByTeamId(teamId).orElseThrow(() -> e);
			}
		}
		// Mở phòng nghĩa là đã xem: mốc đọc bắt đầu từ tin mới nhất để lịch sử cũ không bị tính là chưa đọc
		readState.ensure(c.getId(), me, c.getLastMessageId() == null ? 0 : c.getLastMessageId());
		return views(List.of(c), me).get(0);
	}

	/** Tổng số chưa đọc (huy hiệu trên icon Trò chuyện) và số chưa đọc theo từng cuộc trò chuyện. */
	@Transactional(readOnly = true)
	public UnreadSummary unread() {
		Long me = access.me();
		Map<Long, Long> counts = new LinkedHashMap<>();
		for (UnreadCount u : messages.unreadCounts(me)) {
			counts.put(u.getConversationId(), u.getTotal());
		}
		if (counts.isEmpty()) {
			return new UnreadSummary(0, List.of());
		}
		long total = 0;
		List<UnreadEntry> entries = new ArrayList<>();
		for (Conversation c : conversations.findAllById(counts.keySet())) {
			// Người đã rời nhóm hoặc không còn trong cuộc nhắn thì không tính
			boolean visible = c.isDirect() ? c.isParticipant(me) && c.getStatus() == DirectStatus.ACCEPTED
					: access.memberships().roleOf(c.getTeamId(), me).isPresent();
			if (visible) {
				long n = counts.get(c.getId());
				total += n;
				entries.add(new UnreadEntry(c.getId(), c.getTeamId(), n));
			}
		}
		return new UnreadSummary(total, entries);
	}

	// ---- nội bộ ----

	private void requireRecipient(Conversation c, Long me) {
		if (!c.isDirect() || c.getStatus() != DirectStatus.PENDING) {
			throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "Lời mời này không còn chờ trả lời");
		}
		if (!me.equals(c.recipient())) {
			throw new ForbiddenException("Chỉ người nhận lời mời mới trả lời được");
		}
	}

	private void acceptInternal(Conversation c, Long accepter) {
		Long requester = c.getRequestedBy();
		c.accept(clock.instant());
		conversations.saveAndFlush(c);
		readState.ensure(c.getId(), c.getUserLowId(), 0);
		readState.ensure(c.getId(), c.getUserHighId(), 0);
		events.publishEvent(new ChatRequestAcceptedEvent(c.getId(), accepter, requester));
		push.conversation(List.of(c.getUserLowId(), c.getUserHighId()), c.getId());
	}

	private void requested(Conversation c, Long requester, Long recipient) {
		events.publishEvent(new ChatRequestedEvent(c.getId(), requester, recipient));
		push.conversation(List.of(requester, recipient), c.getId());
	}

	/** Dựng góc nhìn của {@code me} cho một loạt cuộc trò chuyện (tải gộp tin cuối, tên người, nhóm, số chưa đọc). */
	List<ConversationView> views(List<Conversation> list, Long me) {
		if (list.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> unread = new LinkedHashMap<>();
		for (UnreadCount u : messages.unreadCounts(me)) {
			unread.put(u.getConversationId(), u.getTotal());
		}

		Set<Long> lastIds = list.stream().map(Conversation::getLastMessageId).filter(java.util.Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, ChatMessage> lastById = lastIds.isEmpty() ? Map.of()
				: messages.findAllByIds(lastIds).stream().collect(Collectors.toMap(ChatMessage::getId, m -> m));
		Set<Long> withFiles = lastIds.isEmpty() ? Set.of()
				: attachments.findByMessageIdInOrderByIdAsc(lastIds).stream().map(ChatAttachment::getMessageId)
						.collect(Collectors.toSet());

		Set<Long> personIds = new HashSet<>();
		lastById.values().forEach(m -> personIds.add(m.getAuthorId()));
		for (Conversation c : list) {
			if (c.isDirect()) {
				personIds.add(c.otherOf(me));
			}
		}
		Map<Long, PersonView> people = access.people(personIds);

		Set<Long> teamIds = list.stream().filter(Conversation::isTeam).map(Conversation::getTeamId)
				.collect(Collectors.toSet());
		Map<Long, TeamInfo> teamsById = teamIds.isEmpty() ? Map.of()
				: access.teams().findTeams(teamIds).stream().collect(Collectors.toMap(TeamInfo::id, t -> t));

		List<ConversationView> result = new ArrayList<>();
		for (Conversation c : list) {
			ChatMessage last = c.getLastMessageId() == null ? null : lastById.get(c.getLastMessageId());
			LastMessage lastView = last == null ? null
					: new LastMessage(last.getId(), last.getAuthorId(),
							Optional.ofNullable(people.get(last.getAuthorId())).map(PersonView::fullName).orElse(null),
							last.isDeleted() ? null : snippet(last.getBody()), last.isDeleted(),
							!last.isDeleted() && withFiles.contains(last.getId()), last.getCreatedAt());
			TeamInfo team = c.isTeam() ? teamsById.get(c.getTeamId()) : null;
			result.add(new ConversationView(c.getId(), c.getType(), c.getStatus(),
					c.isDirect() && me.equals(c.getRequestedBy()), access.canSend(c),
					c.isDirect() ? people.get(c.otherOf(me)) : null,
					team == null ? null : new TeamRef(team.id(), team.name(), team.active()), lastView,
					unread.getOrDefault(c.getId(), 0L), c.getCreatedAt()));
		}
		return result;
	}

	static String snippet(String body) {
		if (body == null) {
			return null;
		}
		String text = body.strip().replaceAll("\\s+", " ");
		return text.length() <= 120 ? text : text.substring(0, 119) + "…";
	}
}
