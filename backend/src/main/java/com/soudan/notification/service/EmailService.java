package com.soudan.notification.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Gửi email gồm hai phần: HTML (đẹp) và chữ thuần (dự phòng). Nếu chưa cấu hình máy chủ mail
 * ({@code spring.mail.host}) thì chỉ ghi log, không làm hỏng chức năng chính. Lỗi gửi được ném ra để cơ chế sự kiện
 * của Modulith giữ lại và gửi lại sau.
 */
@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private final ObjectProvider<JavaMailSender> sender;
	private final NotificationProperties properties;

	public EmailService(ObjectProvider<JavaMailSender> sender, NotificationProperties properties) {
		this.sender = sender;
		this.properties = properties;
	}

	public void send(String to, MailContent content) {
		JavaMailSender mailSender = sender.getIfAvailable();
		if (mailSender == null) {
			log.warn("Chưa cấu hình máy chủ mail, bỏ qua email gửi tới {}: {}", to, content.subject());
			return;
		}
		String subject = oneLine(content.subject());
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(properties.from());
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(MailTemplate.text(content), MailTemplate.html(content));
			mailSender.send(message);
		} catch (MessagingException e) {
			throw new MailPreparationException(e);
		}
		log.info("Đã gửi email tới {}: {}", to, subject);
	}

	/** Tiêu đề không được chứa xuống dòng (chống chèn header từ nội dung do người dùng nhập). */
	static String oneLine(String text) {
		return text.replaceAll("[\\r\\n]+", " ").strip();
	}
}
