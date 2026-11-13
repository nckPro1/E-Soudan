package com.soudan.report.domain;

import java.time.LocalDate;

import com.soudan.common.base.BaseEntity;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.util.RichText;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Báo cáo hằng ngày của một người trong một cộng đồng: MỘT thân bài rich-text (HTML, soạn bằng Tiptap ở frontend).
 * Mỗi người một báo cáo mỗi ngày mỗi cộng đồng; chỉ sửa được trong chính ngày của báo cáo.
 * Thân bài luôn được lọc bằng danh sách thẻ cho phép trước khi lưu (chống XSS) và phải có chữ thật, không chỉ thẻ rỗng.
 */
@Entity
@Table(name = "report_post")
public class ReportPost extends BaseEntity {

	/** Giới hạn độ dài HTML đã lọc và độ dài chữ thuần. */
	public static final int MAX_HTML_LENGTH = 20000;
	public static final int MAX_TEXT_LENGTH = 10000;
	public static final int MAX_TITLE_LENGTH = 200;

	@Column(nullable = false)
	private Long communityId;

	@Column(nullable = false)
	private Long authorId;

	@Column(nullable = false)
	private LocalDate reportDate;

	@Column(nullable = false)
	private String title;

	/** HTML đã lọc; có thể rỗng khi báo cáo chỉ có tiêu đề và file đính kèm. */
	@Column(columnDefinition = "text")
	private String body;

	protected ReportPost() {
		// JPA
	}

	public static ReportPost create(Long communityId, Long authorId, LocalDate reportDate, String title,
			String body) {
		ReportPost post = new ReportPost();
		post.communityId = communityId;
		post.authorId = authorId;
		post.reportDate = reportDate;
		post.update(title, body);
		return post;
	}

	/** Ghi lại tiêu đề và thân bài: tiêu đề bắt buộc; thân bài được lọc HTML và không quá dài (có thể rỗng). */
	public void update(String newTitle, String html) {
		if (newTitle == null || newTitle.isBlank()) {
			throw new ValidationException("Tiêu đề báo cáo không được để trống");
		}
		String cleanTitle = newTitle.strip();
		if (cleanTitle.length() > MAX_TITLE_LENGTH) {
			throw new ValidationException("Tiêu đề tối đa " + MAX_TITLE_LENGTH + " ký tự");
		}
		String clean = RichText.sanitize(html);
		String text = RichText.plainText(clean);
		if (clean.length() > MAX_HTML_LENGTH || text.length() > MAX_TEXT_LENGTH) {
			throw new ValidationException("Báo cáo quá dài (tối đa " + MAX_TEXT_LENGTH + " ký tự)");
		}
		this.title = cleanTitle;
		this.body = text.isEmpty() ? "" : clean;
	}

	/** Thân bài có chữ thật không (không tính thẻ rỗng). */
	public boolean hasText() {
		return body != null && !RichText.plainText(body).isEmpty();
	}

	/** Có còn sửa được không: chỉ khi hôm nay đúng là ngày của báo cáo. */
	public boolean isEditableOn(LocalDate today) {
		return reportDate.equals(today);
	}

	public Long getCommunityId() {
		return communityId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public LocalDate getReportDate() {
		return reportDate;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}
}
