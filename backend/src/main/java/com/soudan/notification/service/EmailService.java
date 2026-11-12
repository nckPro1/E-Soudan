package com.soudan.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Gửi email văn bản thuần. Nếu chưa cấu hình máy chủ mail ({@code spring.mail.host}) thì chỉ ghi log,
 * không làm hỏng chức năng chính. Lỗi gửi được ném ra để cơ chế sự kiện của Modulith giữ lại và gửi lại sau.
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

	public void send(String to, String subject, String body) {
		JavaMailSender mailSender = sender.getIfAvailable();
		if (mailSender == null) {
			log.warn("Chưa cấu hình máy chủ mail, bỏ qua email gửi tới {}: {}", to, subject);
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.from());
		message.setTo(to);
		message.setSubject(oneLine(subject));
		message.setText(body);
		mailSender.send(message);
		log.info("Đã gửi email tới {}: {}", to, message.getSubject());
	}

	/** Tiêu đề không được chứa xuống dòng (chống chèn header từ nội dung do người dùng nhập). */
	static String oneLine(String text) {
		return text.replaceAll("[\r\n]+", " ").strip();
	}
}
