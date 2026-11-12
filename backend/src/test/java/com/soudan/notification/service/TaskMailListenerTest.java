package com.soudan.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

import com.soudan.common.security.SystemRole;
import com.soudan.notification.service.MailContent.Row;
import com.soudan.notification.service.MailContent.Tone;
import com.soudan.task.TaskAssignedEvent;
import com.soudan.task.TaskCommentedEvent;
import com.soudan.task.TaskSubmittedForReviewEvent;
import com.soudan.team.TeamApi;
import com.soudan.team.TeamInfo;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/** Người nhận và nội dung của các email thông báo (không cần Spring, DB hay máy chủ mail). */
class TaskMailListenerTest {

	private final UserSummary lan = user(1, "Lan", "lan@x.com", true);
	private final UserSummary minh = user(2, "Minh", "minh@x.com", true);
	private final UserSummary hoa = user(3, "Hoa", "hoa@x.com", true);

	private UserApi users;
	private TeamApi teams;
	private EmailService email;
	private TaskMailListener listener;

	private static UserSummary user(long id, String name, String mail, boolean active) {
		return new UserSummary(id, name, mail, null, SystemRole.USER, active);
	}

	/** Dựng nhóm "Alpha" (id 5) với các Leader và những người cho trước. */
	private void givenTeamWith(List<Long> leaderIds, UserSummary... people) {
		when(teams.findLeaderIds(5L)).thenReturn(leaderIds);
		when(users.findSummariesByIds(anyCollection())).thenAnswer(invocation -> {
			Collection<Long> ids = invocation.getArgument(0);
			return Arrays.stream(people).filter(p -> ids.contains(p.id())).toList();
		});
	}

	@BeforeEach
	void setUp() {
		users = mock(UserApi.class);
		teams = mock(TeamApi.class);
		email = mock(EmailService.class);
		listener = new TaskMailListener(users, teams, email,
				new NotificationProperties("no-reply@soudan.local", "https://soudan.example.com/"));
		when(teams.findTeam(5L)).thenReturn(Optional.of(new TeamInfo(5L, "Alpha", true)));
		givenTeamWith(List.of(1L), lan, minh, hoa);
	}

	private MailContent sentTo(String address) {
		ArgumentCaptor<MailContent> captor = ArgumentCaptor.forClass(MailContent.class);
		verify(email).send(eq(address), captor.capture());
		return captor.getValue();
	}

	private static String row(MailContent content, String label) {
		return content.rows().stream().filter(r -> r.label().equals(label)).map(Row::value).findFirst().orElse(null);
	}

	private void verifyNothingSentTo(String address) {
		verify(email, never()).send(eq(address), any(MailContent.class));
	}

	// ---- giao việc ----

	@Test
	void should_mailAssignee_when_taskAssigned() {
		listener.on(new TaskAssignedEvent(9L, 5L, "Viết báo cáo", 2L, 1L, LocalDate.of(2030, 1, 31)));

		MailContent content = sentTo("minh@x.com");
		assertThat(content.subject()).isEqualTo("[E-Soudan] Bạn được giao việc mới: Viết báo cáo");
		assertThat(content.greetingName()).isEqualTo("Minh");
		assertThat(content.badge()).isEqualTo("Được giao");
		assertThat(content.tone()).isEqualTo(Tone.INFO);
		assertThat(row(content, "Công việc")).isEqualTo("Viết báo cáo");
		assertThat(row(content, "Nhóm")).isEqualTo("Alpha");
		assertThat(row(content, "Giao bởi")).isEqualTo("Lan");
		assertThat(row(content, "Hạn chót")).isEqualTo("2030-01-31");
		assertThat(content.link()).isEqualTo("https://soudan.example.com/tasks/9");
		assertThat(content.quote()).isNull();
	}

	@Test
	void should_sayNoDeadline_when_dueDateMissing() {
		listener.on(new TaskAssignedEvent(9L, 5L, "T", 2L, 1L, null));

		assertThat(row(sentTo("minh@x.com"), "Hạn chót")).isEqualTo("Không có");
	}

	@Test
	void should_notMail_when_assigneeLockedOrMissing() {
		givenTeamWith(List.of(1L), lan, user(2, "Minh", "minh@x.com", false));
		listener.on(new TaskAssignedEvent(9L, 5L, "T", 2L, 1L, null));

		givenTeamWith(List.of(1L), lan);
		listener.on(new TaskAssignedEvent(9L, 5L, "T", 2L, 1L, null));

		verify(email, never()).send(any(String.class), any(MailContent.class));
	}

	// ---- chờ duyệt ----

	@Test
	void should_mailEveryActiveLeader_withWarningTone_when_submittedForReview() {
		givenTeamWith(List.of(1L, 3L, 4L), lan, minh, hoa, user(4, "Khoá", "locked@x.com", false));
		when(teams.findLeaderIds(5L)).thenReturn(List.of(1L, 3L, 4L));

		listener.on(new TaskSubmittedForReviewEvent(9L, 5L, "Viết báo cáo", 2L));

		MailContent forLan = sentTo("lan@x.com");
		assertThat(forLan.subject()).isEqualTo("[E-Soudan] Cần duyệt: Viết báo cáo");
		assertThat(forLan.badge()).isEqualTo("Chờ duyệt");
		assertThat(forLan.tone()).isEqualTo(Tone.WARNING);
		assertThat(forLan.greetingName()).isEqualTo("Lan");
		assertThat(row(forLan, "Người thực hiện")).isEqualTo("Minh");
		assertThat(forLan.link()).isEqualTo("https://soudan.example.com/tasks/9");
		sentTo("hoa@x.com");
		verifyNothingSentTo("locked@x.com");
	}

	@Test
	void should_keepMailingOtherLeaders_butStillFail_when_oneMailFails() {
		givenTeamWith(List.of(1L, 3L), lan, minh, hoa);
		when(teams.findLeaderIds(5L)).thenReturn(List.of(1L, 3L));
		doThrow(new MailSendException("smtp down")).when(email).send(eq("lan@x.com"), any(MailContent.class));

		assertThatThrownBy(() -> listener.on(new TaskSubmittedForReviewEvent(9L, 5L, "T", 2L)))
				.isInstanceOf(MailSendException.class);

		sentTo("hoa@x.com");
	}

	// ---- bình luận ----

	@Test
	void should_mailAssigneeAndLeaders_butNotTheAuthor_when_commented() {
		// Minh (người phụ trách) bình luận: chỉ Lan (Leader) nhận
		listener.on(new TaskCommentedEvent(9L, 5L, "Viết báo cáo", 100L, 2L, 2L, null, "Em xong rồi"));

		MailContent content = sentTo("lan@x.com");
		assertThat(content.subject()).isEqualTo("[E-Soudan] Bình luận mới: Viết báo cáo");
		assertThat(content.badge()).isEqualTo("Bình luận mới");
		assertThat(content.heading()).isEqualTo("Minh đã bình luận");
		assertThat(content.quote()).isEqualTo("Em xong rồi");
		assertThat(row(content, "Nhóm")).isEqualTo("Alpha");
		verifyNothingSentTo("minh@x.com");
	}

	@Test
	void should_mailLeaderAndAssignee_when_anotherPersonComments() {
		listener.on(new TaskCommentedEvent(9L, 5L, "T", 100L, 3L, 2L, null, "Hi"));

		sentTo("lan@x.com");
		sentTo("minh@x.com");
		verifyNothingSentTo("hoa@x.com");
	}

	@Test
	void should_sendOneSpecialMail_toAuthorOfRepliedComment() {
		// Hoa trả lời bình luận của Minh (Minh vừa là người phụ trách vừa là người được trả lời): chỉ một thư cho Minh
		listener.on(new TaskCommentedEvent(9L, 5L, "T", 100L, 3L, 2L, 2L, "Ok em"));

		MailContent forMinh = sentTo("minh@x.com");
		assertThat(forMinh.subject()).isEqualTo("[E-Soudan] Hoa đã trả lời bình luận của bạn: T");
		assertThat(forMinh.badge()).isEqualTo("Trả lời bình luận của bạn");
		assertThat(forMinh.heading()).isEqualTo("Hoa đã trả lời bạn");
		verify(email, times(1)).send(eq("minh@x.com"), any(MailContent.class));
		assertThat(sentTo("lan@x.com").subject()).isEqualTo("[E-Soudan] Bình luận mới: T");
	}

	@Test
	void should_notMail_whenNobodyElseToNotify() {
		// Lan vừa là Leader duy nhất vừa là tác giả, người phụ trách là chính Lan (trường hợp biên)
		listener.on(new TaskCommentedEvent(9L, 5L, "T", 100L, 1L, 1L, null, "tự ghi chú"));

		verify(email, never()).send(any(String.class), any(MailContent.class));
	}

	@Test
	void should_skipLockedRecipients_andCutLongComments() {
		givenTeamWith(List.of(1L), lan, user(2, "Minh", "minh@x.com", false), hoa);

		listener.on(new TaskCommentedEvent(9L, 5L, "T", 100L, 3L, 2L, null, "x".repeat(900)));

		MailContent forLan = sentTo("lan@x.com");
		verifyNothingSentTo("minh@x.com");
		assertThat(forLan.quote()).endsWith("…").hasSize(501);
	}

	@Test
	void should_keepMailingOthers_butStillFail_when_oneCommentMailFails() {
		doThrow(new MailSendException("smtp down")).when(email).send(eq("lan@x.com"), any(MailContent.class));

		assertThatThrownBy(() -> listener.on(new TaskCommentedEvent(9L, 5L, "T", 100L, 3L, 2L, null, "Hi")))
				.isInstanceOf(MailSendException.class);

		sentTo("minh@x.com");
	}
}
