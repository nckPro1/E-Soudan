package com.soudan.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.soudan.common.security.SystemRole;
import com.soudan.task.TaskAssignedEvent;
import com.soudan.task.TaskSubmittedForReviewEvent;
import com.soudan.team.TeamApi;
import com.soudan.team.TeamInfo;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/** Nội dung và người nhận của email thông báo (không cần Spring, DB hay máy chủ mail). */
class TaskMailListenerTest {

	private UserApi users;
	private TeamApi teams;
	private EmailService email;
	private TaskMailListener listener;

	private static UserSummary user(long id, String name, String mail, SystemRole role, boolean active) {
		return new UserSummary(id, name, mail, null, role, active);
	}

	@BeforeEach
	void setUp() {
		users = mock(UserApi.class);
		teams = mock(TeamApi.class);
		email = mock(EmailService.class);
		listener = new TaskMailListener(users, teams, email,
				new NotificationProperties("no-reply@soudan.local", "https://soudan.example.com/"));

		when(teams.findTeam(5L)).thenReturn(Optional.of(new TeamInfo(5L, "Alpha", true)));
		when(users.findSummariesByIds(List.of(2L))).thenReturn(List.of(user(2, "Minh", "minh@x.com", SystemRole.USER, true)));
		when(users.findSummariesByIds(List.of(1L))).thenReturn(List.of(user(1, "Lan", "lan@x.com", SystemRole.USER, true)));
	}

	@Test
	void should_mailAssignee_when_taskAssigned() {
		listener.on(new TaskAssignedEvent(9L, 5L, "Viết báo cáo", 2L, 1L, LocalDate.of(2030, 1, 31)));

		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(email).send(eq("minh@x.com"), subject.capture(), body.capture());
		assertThat(subject.getValue()).isEqualTo("[E-Soudan] Bạn được giao việc mới: Viết báo cáo");
		assertThat(body.getValue()).contains("Xin chào Minh", "Lan vừa giao", "nhóm Alpha", "Viết báo cáo", "2030-01-31")
				.contains("https://soudan.example.com/tasks/9");
	}

	@Test
	void should_sayNoDeadline_when_dueDateMissing() {
		listener.on(new TaskAssignedEvent(9L, 5L, "T", 2L, 1L, null));

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(email).send(eq("minh@x.com"), anyString(), body.capture());
		assertThat(body.getValue()).contains("Hạn chót: không có");
	}

	@Test
	void should_notMail_when_assigneeLocked() {
		when(users.findSummariesByIds(List.of(2L))).thenReturn(List.of(user(2, "Minh", "minh@x.com", SystemRole.USER, false)));

		listener.on(new TaskAssignedEvent(9L, 5L, "T", 2L, 1L, null));

		verify(email, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void should_notMail_when_assigneeMissing() {
		when(users.findSummariesByIds(List.of(2L))).thenReturn(List.of());

		listener.on(new TaskAssignedEvent(9L, 5L, "T", 2L, 1L, null));

		verify(email, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void should_mailEveryActiveLeader_when_submittedForReview() {
		when(teams.findLeaderIds(5L)).thenReturn(List.of(1L, 3L, 4L));
		when(users.findSummariesByIds(List.of(1L, 3L, 4L))).thenReturn(List.of(
				user(1, "Lan", "lan@x.com", SystemRole.USER, true),
				user(3, "Hoa", "hoa@x.com", SystemRole.USER, true),
				user(4, "Khoá", "locked@x.com", SystemRole.USER, false)));

		listener.on(new TaskSubmittedForReviewEvent(9L, 5L, "Viết báo cáo", 2L));

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(email).send(eq("lan@x.com"), eq("[E-Soudan] Cần duyệt: Viết báo cáo"), body.capture());
		verify(email).send(eq("hoa@x.com"), anyString(), anyString());
		verify(email, never()).send(eq("locked@x.com"), anyString(), anyString());
		assertThat(body.getValue()).contains("Xin chào Lan", "Minh đã đánh dấu hoàn thành", "Viết báo cáo",
				"https://soudan.example.com/tasks/9");
	}

	@Test
	void should_keepMailingOtherLeaders_butStillFail_when_oneMailFails() {
		when(teams.findLeaderIds(5L)).thenReturn(List.of(1L, 3L));
		when(users.findSummariesByIds(List.of(1L, 3L))).thenReturn(List.of(
				user(1, "Lan", "lan@x.com", SystemRole.USER, true),
				user(3, "Hoa", "hoa@x.com", SystemRole.USER, true)));
		doThrow(new MailSendException("smtp down")).when(email).send(eq("lan@x.com"), anyString(), anyString());

		assertThatThrownBy(() -> listener.on(new TaskSubmittedForReviewEvent(9L, 5L, "T", 2L)))
				.isInstanceOf(MailSendException.class);

		verify(email).send(eq("hoa@x.com"), anyString(), anyString());
	}

	// ---- EmailService ----

	@Test
	@SuppressWarnings("unchecked")
	void should_sendOneLineSubjectFromConfiguredAddress() {
		JavaMailSender sender = mock(JavaMailSender.class);
		ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(sender);
		EmailService service = new EmailService(provider, new NotificationProperties("no-reply@soudan.local", "http://x"));

		service.send("a@x.com", "Dòng 1\r\nBcc: evil@x.com", "nội dung");

		ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(sender).send(message.capture());
		assertThat(message.getValue().getFrom()).isEqualTo("no-reply@soudan.local");
		assertThat(message.getValue().getTo()).containsExactly("a@x.com");
		assertThat(message.getValue().getSubject()).doesNotContain("\n").doesNotContain("\r");
		assertThat(message.getValue().getText()).isEqualTo("nội dung");
	}

	@Test
	@SuppressWarnings("unchecked")
	void should_skipQuietly_when_noMailServerConfigured() {
		ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		EmailService service = new EmailService(provider, new NotificationProperties("a@x.com", "http://x"));

		service.send("a@x.com", "s", "b");

		verify(provider, times(1)).getIfAvailable();
		verify(provider, never()).getObject();
	}
}
