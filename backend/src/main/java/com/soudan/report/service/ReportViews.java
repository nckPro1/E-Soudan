package com.soudan.report.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Các dạng dữ liệu service trả ra (đã ghép tên người, dựng trong giao dịch). */
public final class ReportViews {

	private ReportViews() {
	}

	public record PersonView(Long id, String fullName, String position) {
	}

	/** Một loại biểu cảm: bao nhiêu người thả, mình có thả không, và tên những người đã thả. */
	public record ReactionView(String emoji, int count, boolean reactedByMe, List<String> users) {
	}

	public record CommunitySummary(Long id, String name, String description, PersonView owner, int memberCount,
			boolean owned) {
	}

	public record CommunityDetail(CommunitySummary community, List<PersonView> members, Long version) {
	}

	public record PostView(Long id, Long communityId, PersonView author, LocalDate reportDate, String title,
			String body, List<AttachmentView> attachments, boolean editable, boolean edited, Instant createdAt, Long version,
			int commentCount, List<ReactionView> reactions) {
	}

	public record AttachmentView(Long id, String fileName, String contentType, long sizeBytes, boolean image) {
	}

	/** {@code deleted} true: bình luận gốc đã xoá nhưng còn trả lời, nên {@code author} và {@code body} là null. */
	public record CommentView(Long id, Long parentId, PersonView author, String body, boolean deleted,
			Instant createdAt, Instant editedAt, List<ReactionView> reactions) {
	}

	/** Người chưa nộp báo cáo trong một ngày (chỉ chủ cộng đồng xem). */
	public record PendingView(LocalDate date, boolean workingDay, List<PersonView> missing, List<PersonView> onLeave) {
	}
}
