package com.soudan.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import com.soudan.notification.service.MailContent.Row;
import com.soudan.notification.service.MailContent.Tone;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Cấu trúc thư thật sự được gửi đi (MIME): hai phần HTML + chữ thuần, người gửi, người nhận, tiêu đề. */
class EmailServiceTest {

	private static MailContent content(String subject) {
		return new MailContent(subject, "xem trước", "Chờ duyệt", Tone.WARNING, "Tiêu đề lớn", "Minh", "Câu dẫn",
				List.of(new Row("Công việc", "Báo cáo")), null, "Mở công việc", "https://x.test/tasks/1");
	}

	@SuppressWarnings("unchecked")
	private static EmailService serviceWith(JavaMailSender sender) {
		ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(sender);
		return new EmailService(provider, new NotificationProperties("E-Soudan <no-reply@soudan.local>", "https://x.test"));
	}

	/** Ghi thư ra byte rồi đọc lại, đúng như người nhận sẽ nhận được (cấu trúc MIME hoàn chỉnh, đã giải mã). */
	private static MimeMessage reparsed(MimeMessage message) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		message.writeTo(out);
		return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(out.toByteArray()));
	}

	/** Tìm phần có kiểu nội dung cho trước trong thư nhiều phần. */
	private static String partOf(Part part, String mimeType) throws Exception {
		if (part.isMimeType(mimeType)) {
			return (String) part.getContent();
		}
		if (part.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) part.getContent();
			for (int i = 0; i < multipart.getCount(); i++) {
				String found = partOf(multipart.getBodyPart(i), mimeType);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	@Test
	void should_sendHtmlAndPlainTextParts_toTheRecipient() throws Exception {
		JavaMailSender sender = mock(JavaMailSender.class);
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(sender.createMimeMessage()).thenReturn(message);

		serviceWith(sender).send("minh@x.com", content("[E-Soudan] Cần duyệt: Báo cáo"));

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(sender).send(captor.capture());
		MimeMessage sent = reparsed(captor.getValue());
		assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("minh@x.com");
		assertThat(sent.getFrom()[0].toString()).contains("no-reply@soudan.local").contains("E-Soudan");
		assertThat(sent.getSubject()).isEqualTo("[E-Soudan] Cần duyệt: Báo cáo");
		String html = partOf(sent, "text/html");
		String text = partOf(sent, "text/plain");
		assertThat(html).contains("<!DOCTYPE html>", "Tiêu đề lớn", "href=\"https://x.test/tasks/1\"");
		assertThat(text).contains("Xin chào Minh", "Công việc: Báo cáo", "https://x.test/tasks/1");
	}

	@Test
	void should_keepSubjectOnOneLine_evenWhenTitleContainsNewlines() throws Exception {
		JavaMailSender sender = mock(JavaMailSender.class);
		when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

		serviceWith(sender).send("a@x.com", content("Dòng 1\r\nBcc: evil@x.com"));

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(sender).send(captor.capture());
		assertThat(captor.getValue().getSubject()).doesNotContain("\n").doesNotContain("\r");
		assertThat(captor.getValue().getHeader("Bcc")).isNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void should_skipQuietly_when_noMailServerConfigured() {
		ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		EmailService service = new EmailService(provider, new NotificationProperties("a@x.com", "http://x"));

		service.send("a@x.com", content("s"));

		verify(provider, never()).getObject();
		assertThat(provider.getIfAvailable()).isNull();
		verify(provider, never()).getIfUnique();
		verify(mock(JavaMailSender.class), never()).send(any(MimeMessage.class));
	}
}
