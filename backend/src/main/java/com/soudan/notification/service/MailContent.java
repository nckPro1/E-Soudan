package com.soudan.notification.service;

import java.util.List;

/**
 * Nội dung một email thông báo, tách khỏi cách hiển thị: {@link MailTemplate} dựng bản HTML và bản chữ thuần
 * từ cùng một đối tượng này.
 *
 * @param subject tiêu đề thư
 * @param preheader dòng xem trước hiện cạnh tiêu đề trong hộp thư
 * @param badge nhãn nhỏ phía trên tiêu đề (ví dụ "Chờ duyệt"); có thể null
 * @param tone màu của nhãn
 * @param heading tiêu đề lớn trong thư
 * @param greetingName tên người nhận để chào
 * @param intro câu dẫn
 * @param rows các dòng thông tin (nhãn: giá trị)
 * @param quote đoạn trích (ví dụ nội dung bình luận); có thể null
 * @param buttonLabel chữ trên nút
 * @param link đường dẫn của nút
 */
public record MailContent(String subject, String preheader, String badge, Tone tone, String heading,
		String greetingName, String intro, List<Row> rows, String quote, String buttonLabel, String link) {

	public enum Tone {
		/** Chàm: thông tin, được giao việc, bình luận. */
		INFO,
		/** Cam: cần hành động (chờ duyệt). */
		WARNING
	}

	public record Row(String label, String value) {
	}
}
