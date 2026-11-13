package com.soudan.calendar.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import com.soudan.common.exception.ValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một ghi chú đánh dấu trên lịch cá nhân. Chỉ chủ sở hữu thấy và sửa được. */
@Entity
@Table(name = "calendar_note")
public class CalendarNote {

	public static final int MAX_TITLE = 100;
	public static final int MAX_CONTENT = 2000;
	/** Các màu đánh dấu cho phép (giao diện chọn từ danh sách này). */
	public static final Set<String> COLORS = Set.of("INDIGO", "RED", "GREEN", "ORANGE", "BLUE", "PURPLE");
	public static final String DEFAULT_COLOR = "INDIGO";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private LocalDate noteDate;

	@Column(nullable = false, length = MAX_TITLE)
	private String title;

	@Column(length = MAX_CONTENT)
	private String content;

	@Column(nullable = false, length = 10)
	private String color;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected CalendarNote() {
		// JPA
	}

	public CalendarNote(Long userId, LocalDate noteDate, String title, String content, String color, Instant now) {
		this.userId = userId;
		this.createdAt = now;
		change(noteDate, title, content, color, now);
	}

	/** Đặt nội dung mới; chuẩn hoá chữ và kiểm tra giới hạn. */
	public void change(LocalDate noteDate, String title, String content, String color, Instant now) {
		String cleanTitle = title == null ? "" : title.strip();
		if (cleanTitle.isEmpty() || cleanTitle.length() > MAX_TITLE) {
			throw new ValidationException("Tiêu đề ghi chú phải từ 1 đến " + MAX_TITLE + " ký tự");
		}
		String cleanContent = content == null || content.isBlank() ? null : content.strip();
		if (cleanContent != null && cleanContent.length() > MAX_CONTENT) {
			throw new ValidationException("Nội dung ghi chú tối đa " + MAX_CONTENT + " ký tự");
		}
		String cleanColor = color == null || color.isBlank() ? DEFAULT_COLOR : color;
		if (!COLORS.contains(cleanColor)) {
			throw new ValidationException("Màu ghi chú không hợp lệ");
		}
		if (noteDate == null) {
			throw new ValidationException("Hãy chọn ngày cho ghi chú");
		}
		this.noteDate = noteDate;
		this.title = cleanTitle;
		this.content = cleanContent;
		this.color = cleanColor;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public LocalDate getNoteDate() {
		return noteDate;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public String getColor() {
		return color;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
