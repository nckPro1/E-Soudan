package com.soudan.chat.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.chat.ChatApi;
import com.soudan.chat.domain.ChatMessage;
import com.soudan.chat.domain.Conversation;
import com.soudan.chat.repository.ChatMessageRepository;
import com.soudan.chat.repository.ConversationRepository;
import com.soudan.chat.service.ChatViews.PersonView;
import com.soudan.common.ai.KnowledgeDoc;
import com.soudan.common.ai.KnowledgeSource;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.team.TeamInfo;

/**
 * Tin nhắn cho trợ lý AI. Truy xuất chung (hỏi đáp) chỉ lấy phòng chat của các nhóm mình thuộc, KHÔNG bao giờ lấy nhắn
 * riêng. Tóm tắt một cuộc trò chuyện (kể cả nhắn riêng) chỉ khi người dùng chủ động chọn đúng cuộc đó và có quyền xem.
 */
@Component
@Transactional(readOnly = true)
class ChatKnowledge implements ChatApi, KnowledgeSource {

	private static final int MAX_ROWS = 1500;
	private static final int MAX_DAY_TEXT = 6000;

	private final ChatMessageRepository messages;
	private final ConversationRepository conversations;
	private final ChatAccess access;
	private final CurrentUser currentUser;
	private final AccessPolicy policy;
	private final Clock clock;
	private final ZoneId zone;

	ChatKnowledge(ChatMessageRepository messages, ConversationRepository conversations, ChatAccess access,
			CurrentUser currentUser, AccessPolicy policy, Clock clock, ZoneId zone) {
		this.messages = messages;
		this.conversations = conversations;
		this.access = access;
		this.currentUser = currentUser;
		this.policy = policy;
		this.clock = clock;
		this.zone = zone;
	}

	@Override
	public String kind() {
		return "TEAM_CHAT";
	}

	@Override
	public KnowledgeDoc transcript(Long conversationId, Instant since, int limit) {
		Long me = access.me();
		Conversation conversation = access.requireAccess(conversationId, me);
		List<ChatMessage> rows = new ArrayList<>(
				messages.liveSince(List.of(conversationId), since, PageRequest.of(0, Math.min(limit, MAX_ROWS))));
		Collections.reverse(rows);
		Map<Long, PersonView> people = access.people(rows.stream().map(ChatMessage::getAuthorId).collect(Collectors.toSet()));
		String title = conversation.isTeam()
				? "Chat nhóm " + access.teams().findTeam(conversation.getTeamId()).map(TeamInfo::name).orElse("")
				: "Nhắn riêng với " + access.people(Set.of(conversation.otherOf(me))).values().stream().findFirst()
						.map(PersonView::fullName).orElse("");
		String path = conversation.isTeam() ? "/teams/" + conversation.getTeamId() + "?tab=chat"
				: "/chat/" + conversationId;
		return new KnowledgeDoc("CHAT", conversationId, title, lines(rows, people, true), clock.instant(), null, path);
	}

	@Override
	public List<KnowledgeDoc> visibleSince(Instant since, int limit) {
		if (policy.isAdmin()) {
			return List.of();
		}
		Long me = currentUser.require().id();
		List<Long> teamIds = access.teams().findTeamIdsOf(me);
		if (teamIds.isEmpty()) {
			return List.of();
		}
		List<Conversation> rooms = conversations.findByTeamIds(teamIds);
		if (rooms.isEmpty()) {
			return List.of();
		}
		Map<Long, Conversation> byId = rooms.stream().collect(Collectors.toMap(Conversation::getId, c -> c));
		List<ChatMessage> rows = messages.liveSince(byId.keySet(), since, PageRequest.of(0, MAX_ROWS));
		Map<Long, TeamInfo> teamInfo = access.teams().findTeams(new HashSet<>(teamIds)).stream()
				.collect(Collectors.toMap(TeamInfo::id, t -> t));
		Map<Long, PersonView> people = access.people(rows.stream().map(ChatMessage::getAuthorId).collect(Collectors.toSet()));

		// Gộp theo (phòng, ngày) để một mẩu là một ngày trao đổi của một nhóm
		Map<String, List<ChatMessage>> groups = new LinkedHashMap<>();
		for (ChatMessage m : rows) {
			String key = m.getConversationId() + "|" + m.getCreatedAt().atZone(zone).toLocalDate();
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
		}
		List<KnowledgeDoc> result = new ArrayList<>();
		for (Map.Entry<String, List<ChatMessage>> entry : groups.entrySet()) {
			List<ChatMessage> day = new ArrayList<>(entry.getValue());
			Collections.reverse(day); // rows mới nhất trước; trong ngày đọc cũ trước mới sau
			ChatMessage first = day.get(0);
			Conversation room = byId.get(first.getConversationId());
			String teamName = teamInfo.containsKey(room.getTeamId()) ? teamInfo.get(room.getTeamId()).name() : "";
			String text = lines(day, people, false);
			String date = first.getCreatedAt().atZone(zone).toLocalDate().toString();
			result.add(new KnowledgeDoc("TEAM_CHAT", first.getConversationId(), "Chat nhóm " + teamName + " ngày " + date,
					text.length() > MAX_DAY_TEXT ? text.substring(0, MAX_DAY_TEXT) + "…" : text,
					day.get(day.size() - 1).getCreatedAt(), null, "/teams/" + room.getTeamId() + "?tab=chat"));
			if (result.size() >= limit) {
				break;
			}
		}
		return result;
	}

	private String lines(List<ChatMessage> rows, Map<Long, PersonView> people, boolean withDate) {
		DateTimeFormatter format = DateTimeFormatter.ofPattern(withDate ? "dd/MM HH:mm" : "HH:mm").withZone(zone);
		StringBuilder out = new StringBuilder();
		for (ChatMessage m : rows) {
			PersonView who = people.get(m.getAuthorId());
			out.append('[').append(format.format(m.getCreatedAt())).append("] ")
					.append(who == null ? "?" : who.fullName()).append(": ")
					.append(m.getBody() == null ? "(tệp đính kèm)" : m.getBody().replace('\n', ' ')).append('\n');
		}
		return out.toString();
	}
}
