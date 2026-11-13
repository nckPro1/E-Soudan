package com.soudan.chat.service;

import java.time.Instant;
import java.util.List;

import com.soudan.chat.domain.ConversationType;
import com.soudan.chat.domain.DirectStatus;

/** Các dạng dữ liệu service trả ra (đã ghép tên người, dựng trong giao dịch). */
public final class ChatViews {

	private ChatViews() {
	}

	public record PersonView(Long id, String fullName, String position) {
	}

	public record TeamRef(Long id, String name, boolean active) {
	}

	/** Tin cuối của một cuộc trò chuyện, để hiện ở danh sách. {@code snippet} null khi tin đã xoá hoặc chỉ có file. */
	public record LastMessage(Long id, Long authorId, String authorName, String snippet, boolean deleted,
			boolean hasAttachment, Instant at) {
	}

	/**
	 * Một cuộc trò chuyện theo góc nhìn của người đang xem. {@code other} có với DIRECT, {@code team} có với TEAM.
	 * {@code requestedByMe} chỉ có nghĩa khi DIRECT đang PENDING: mình là bên gửi lời mời (chờ) hay bên nhận (phải trả lời).
	 */
	public record ConversationView(Long id, ConversationType type, DirectStatus status, boolean requestedByMe,
			boolean canSend, PersonView other, TeamRef team, LastMessage last, long unread, Instant createdAt) {
	}

	public record AttachmentView(Long id, String fileName, String contentType, long sizeBytes, boolean image) {
	}

	public record ReactionView(String emoji, int count, boolean reactedByMe, List<String> users) {
	}

	/** Tin được trích dẫn khi trả lời. */
	public record ReplyRef(Long id, PersonView author, String snippet, boolean deleted) {
	}

	/** {@code deleted} true: tin đã xoá, nên {@code body}, {@code attachments}, {@code reactions} đều rỗng. */
	public record MessageView(Long id, Long conversationId, PersonView author, String body, boolean deleted,
			ReplyRef replyTo, List<AttachmentView> attachments, List<ReactionView> reactions, Instant createdAt,
			Instant editedAt) {
	}

	/** Một trang tin, mới nhất trước. {@code hasMore} true: còn tin cũ hơn để cuộn lên xem. */
	public record MessagePage(List<MessageView> items, boolean hasMore) {
	}

	/** Số tin chưa đọc của một cuộc trò chuyện (kèm id nhóm nếu là phòng chat của nhóm). */
	public record UnreadEntry(Long conversationId, Long teamId, long count) {
	}

	public record UnreadSummary(long total, List<UnreadEntry> conversations) {
	}
}
