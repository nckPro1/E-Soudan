package com.soudan.report.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.ai.KnowledgeDoc;
import com.soudan.common.ai.KnowledgeSource;
import com.soudan.common.security.CurrentUser;
import com.soudan.common.util.RichText;
import com.soudan.report.ReportApi;
import com.soudan.report.domain.Community;
import com.soudan.report.domain.ReportPost;
import com.soudan.report.repository.CommunityMemberRepository;
import com.soudan.report.repository.CommunityRepository;
import com.soudan.report.repository.ReportDayOffRepository;
import com.soudan.report.repository.ReportPostRepository;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Báo cáo cho trợ lý AI và cho việc nhắc nộp báo cáo. Quyền giống hệt feed thường: chủ cộng đồng thấy mọi báo cáo trong cộng đồng của mình, thành viên
 * thấy báo cáo của nhau trong cộng đồng mình tham gia; Admin và người ngoài không thấy gì.
 */
@Component
@Transactional(readOnly = true)
class ReportKnowledge implements ReportApi, KnowledgeSource {

	private static final int MAX_TEXT = 4000;

	private final ReportPostRepository posts;
	private final CommunityMemberRepository members;
	private final ReportDayOffRepository dayOffs;
	private final ReportProperties properties;
	private final CommunityRepository communities;
	private final CommunityAccess access;
	private final CurrentUser currentUser;
	private final UserApi users;
	private final Clock clock;
	private final ZoneId zone;

	ReportKnowledge(ReportPostRepository posts, CommunityMemberRepository members, ReportDayOffRepository dayOffs,
			ReportProperties properties, CommunityRepository communities, CommunityAccess access,
			CurrentUser currentUser, UserApi users, Clock clock, ZoneId zone) {
		this.posts = posts;
		this.members = members;
		this.dayOffs = dayOffs;
		this.properties = properties;
		this.communities = communities;
		this.access = access;
		this.currentUser = currentUser;
		this.users = users;
		this.clock = clock;
		this.zone = zone;
	}

	@Override
	public String kind() {
		return "REPORT";
	}

	@Override
	public CommunityDigest communityPosts(Long communityId, LocalDate from, LocalDate to, int limit) {
		Community community = access.requireParticipant(communityId);
		List<ReportPost> rows = posts.inCommunitiesBetween(List.of(communityId), from, to, PageRequest.of(0, limit));
		return new CommunityDigest(community.getName(), docs(rows, Map.of(community.getId(), community.getName())));
	}

	@Override
	public List<MissingReports> missingOn(LocalDate day) {
		if (!properties.isWorkDay(day)) {
			return List.of();
		}
		Set<Long> onLeave = new HashSet<>(dayOffs.userIdsOn(day));
		List<MissingReports> result = new ArrayList<>();
		for (Community community : communities.findAllLive()) {
			Set<Long> submitted = new HashSet<>(posts.authorsOfDay(community.getId(), day));
			List<Long> memberIds = members.findByCommunityIdOrderByAddedAtAscIdAsc(community.getId()).stream()
					.map(m -> m.getUserId()).toList();
			Set<Long> active = access.activeIds(memberIds);
			List<Long> missing = memberIds.stream()
					.filter(id -> active.contains(id) && !submitted.contains(id) && !onLeave.contains(id)).toList();
			if (!missing.isEmpty()) {
				result.add(new MissingReports(community.getId(), community.getName(), missing));
			}
		}
		return result;
	}

	@Override
	public List<KnowledgeDoc> visibleSince(Instant since, int limit) {
		Long me = currentUser.require().id();
		Map<Long, String> names = new LinkedHashMap<>();
		communities.findOwnedBy(me).forEach(c -> names.put(c.getId(), c.getName()));
		communities.findJoinedBy(me).forEach(c -> names.put(c.getId(), c.getName()));
		if (names.isEmpty()) {
			return List.of();
		}
		LocalDate from = LocalDate.ofInstant(since, zone);
		LocalDate to = LocalDate.now(clock.withZone(zone)).plusDays(1);
		return docs(posts.inCommunitiesBetween(names.keySet(), from, to, PageRequest.of(0, limit)), names);
	}

	private List<KnowledgeDoc> docs(List<ReportPost> rows, Map<Long, String> communityNames) {
		Set<Long> authorIds = rows.stream().map(ReportPost::getAuthorId).collect(Collectors.toCollection(HashSet::new));
		Map<Long, String> authors = authorIds.isEmpty() ? Map.of()
				: users.findSummariesByIds(authorIds).stream()
						.collect(Collectors.toMap(UserSummary::id, UserSummary::fullName));
		List<KnowledgeDoc> result = new ArrayList<>();
		for (ReportPost p : rows) {
			String text = RichText.plainText(p.getBody());
			if (text.length() > MAX_TEXT) {
				text = text.substring(0, MAX_TEXT) + "…";
			}
			String community = communityNames.getOrDefault(p.getCommunityId(), "");
			String title = "Báo cáo " + p.getReportDate() + " · " + community + " · " + p.getTitle();
			result.add(new KnowledgeDoc("REPORT", p.getId(), title, text,
					p.getReportDate().atStartOfDay().toInstant(ZoneOffset.UTC), authors.get(p.getAuthorId()),
					"/communities/" + p.getCommunityId() + "/members/" + p.getAuthorId()));
		}
		return result;
	}
}
