package com.soudan.chat.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.chat.domain.ChatAttachment;
import com.soudan.chat.domain.ChatMessage;
import com.soudan.chat.domain.ChatReaction;
import com.soudan.chat.domain.Conversation;
import com.soudan.chat.repository.ChatAttachmentRepository;
import com.soudan.chat.repository.ChatMessageRepository;
import com.soudan.chat.repository.ChatReactionRepository;
import com.soudan.chat.repository.ConversationRepository;
import com.soudan.chat.service.ChatViews.AttachmentView;
import com.soudan.chat.service.ChatViews.MessagePage;
import com.soudan.chat.service.ChatViews.MessageView;
import com.soudan.chat.service.ChatViews.PersonView;
import com.soudan.chat.service.ChatViews.ReactionView;
import com.soudan.chat.service.ChatViews.ReplyRef;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.file.FileStorage;
import com.soudan.common.file.UploadPolicy;
import com.soudan.common.util.Emoji;

/**
 * Tin nhắn trong một cuộc trò chuyện: đọc lịch sử (phân trang theo con trỏ), gửi (chữ, file, ảnh, trả lời một tin), sửa
 * và xoá tin của mình, thả biểu cảm, đánh dấu đã đọc. Lịch sử được giữ vĩnh viễn: xoá chỉ là xoá mềm, nội dung bị ẩn khi
 * hiển thị nhưng vẫn nằm trong CSDL. Mọi thay đổi được đẩy tới những người trong cuộc qua WebSocket sau khi commit.
 */
@Service
@Transactional
public class MessageService {

	public static final int DEFAULT_PAGE = 30;
	public static final int MAX_PAGE = 100;

	/** Một file người dùng vừa chọn, chưa lưu. Người gọi đóng {@code content}. */
	public record NewFile(String originalName, long sizeBytes, InputStream content) {
	}

	/** Một file đã kiểm tra, sẵn sàng lưu (luồng đã đọc đầu file nên có thể đọc lại từ đầu). */
	private record Prepared(String name, long size, BufferedInputStream content) {
	}

	/** Một file đã mở để tải; người gọi đóng {@code content}. */
	public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
	}

	private final ChatMessageRepository messages;
	private final ConversationRepository conversations;
	private final ChatAttachmentRepository attachments;
	private final ChatReactionRepository reactions;
	private final ChatAccess access;
	private final ChatReadState readState;
	private final ChatPush push;
	private final FileStorage storage;
	private final UploadPolicy uploadPolicy;
	private final Clock clock;

	public MessageService(ChatMessageRepository messages, ConversationRepository conversations,
			ChatAttachmentRepository attachments, ChatReactionRepository reactions, ChatAccess access,
			ChatReadState readState, ChatPush push, FileStorage storage, UploadPolicy uploadPolicy, Clock clock) {
		this.messages = messages;
		this.conversations = conversations;
		this.attachments = attachments;
		this.reactions = reactions;
		this.access = access;
		this.readState = readState;
		this.push = push;
		this.storage = storage;
		this.uploadPolicy = uploadPolicy;
		this.clock = clock;
	}

	// ---- đọc ----

	/**
	 * Một trang lịch sử, mới nhất trước. Không có {@code beforeId} là trang mới nhất; có thì lấy các tin cũ hơn mốc đó
	 * (cuộn lên xem thêm). Người xem được coi là đã đọc tới tin mới nhất của trang đầu.
	 */
	public MessagePage list(Long conversationId, Long beforeId, int size) {
		Long me = access.me();
		access.requireAccess(conversationId, me);
		int limit = Math.min(Math.max(size, 1), MAX_PAGE);
		PageRequest paging = PageRequest.of(0, limit + 1);
		List<ChatMessage> rows = beforeId == null ? messages.latest(conversationId, paging)
				: messages.olderThan(conversationId, beforeId, paging);
		boolean hasMore = rows.size() > limit;
		List<ChatMessage> page = hasMore ? rows.subList(0, limit) : rows;
		return new MessagePage(views(page, me), hasMore);
	}

	@Transactional(readOnly = true)
	public MessageView get(Long messageId) {
		Long me = access.me();
		ChatMessage m = load(messageId);
		access.requireAccess(m.getConversationId(), me);
		return views(List.of(m), me).get(0);
	}

	/** Đánh dấu đã đọc tới tin mới nhất của cuộc trò chuyện. */
	public void markRead(Long conversationId) {
		Long me = access.me();
		Conversation c = access.requireAccess(conversationId, me);
		if (c.getLastMessageId() != null) {
			readState.advance(conversationId, me, c.getLastMessageId());
		} else {
			readState.ensure(conversationId, me, 0);
		}
	}

	// ---- gửi, sửa, xoá ----

	/**
	 * Gửi một tin. Phải có chữ hoặc ít nhất một file. Mọi file được kiểm tra (đuôi, kích thước, nội dung) trước khi lưu
	 * bất cứ thứ gì. {@code replyToId} là tin được trả lời (phải cùng cuộc trò chuyện).
	 */
	public MessageView send(Long conversationId, String body, Long replyToId, List<NewFile> files) {
		Long me = access.me();
		Conversation c = access.requireAccess(conversationId, me);
		access.requireCanSend(c);

		String text = ChatMessage.clean(body);
		List<NewFile> upload = files == null ? List.of() : files;
		if (text == null && upload.isEmpty()) {
			throw new ValidationException(ErrorCode.EMPTY_MESSAGE, ErrorCode.EMPTY_MESSAGE.defaultMessage());
		}
		if (upload.size() > ChatAttachment.MAX_PER_MESSAGE) {
			throw new ValidationException(ErrorCode.TOO_MANY_ATTACHMENTS_CHAT,
					ErrorCode.TOO_MANY_ATTACHMENTS_CHAT.defaultMessage());
		}
		if (replyToId != null) {
			ChatMessage target = load(replyToId);
			if (!target.getConversationId().equals(conversationId) || target.isDeleted()) {
				throw new NotFoundException("Không tìm thấy tin nhắn " + replyToId);
			}
		}
		List<Prepared> prepared = prepare(upload);
		List<String> storedKeys = new ArrayList<>();
		try {
			Long previousLast = c.getLastMessageId();
			ChatMessage saved = messages.saveAndFlush(new ChatMessage(conversationId, me, text, replyToId, clock.instant()));
			for (Prepared file : prepared) {
				String key = storage.save(file.content());
				storedKeys.add(key);
				attachments.save(new ChatAttachment(saved.getId(), file.name(), uploadPolicy.contentTypeOf(file.name()),
						file.size(), key));
			}
			attachments.flush();
			c.touch(saved.getId(), saved.getCreatedAt());
			conversations.save(c);

			// Mốc đọc: tin của chính mình thì mình đã đọc; người khác chưa có mốc (vào nhóm sau) thì đặt ngay trước tin này
			Set<Long> audience = access.audience(c);
			for (Long userId : audience) {
				if (userId.equals(me)) {
					readState.advance(conversationId, me, saved.getId());
				} else {
					readState.ensure(conversationId, userId, previousLast == null ? 0 : previousLast);
				}
			}
			MessageView view = views(List.of(saved), me).get(0);
			push.message(audience, view);
			return view;
		} catch (IOException e) {
			storedKeys.forEach(this::deleteQuietly);
			throw new UncheckedIOException(e);
		} catch (RuntimeException e) {
			storedKeys.forEach(this::deleteQuietly);
			throw e;
		}
	}

	/** Chỉ tác giả sửa được tin của mình. */
	public MessageView edit(Long messageId, String body) {
		Long me = access.me();
		ChatMessage m = load(messageId);
		Conversation c = access.requireAccess(m.getConversationId(), me);
		access.requireCanSend(c);
		requireAuthor(m, me);
		if (m.isDeleted()) {
			throw new NotFoundException("Không tìm thấy tin nhắn " + messageId);
		}
		m.edit(body, clock.instant());
		messages.saveAndFlush(m);
		push.messageUpdated(access.audience(c), c.getId(), m.getId());
		return views(List.of(m), me).get(0);
	}

	/** Chỉ tác giả xoá được tin của mình. Xoá mềm: nội dung bị ẩn nhưng lịch sử vẫn còn trong CSDL. */
	public void delete(Long messageId) {
		Long me = access.me();
		ChatMessage m = load(messageId);
		Conversation c = access.requireAccess(m.getConversationId(), me);
		access.requireCanSend(c);
		requireAuthor(m, me);
		m.markDeleted(clock.instant());
		messages.saveAndFlush(m);
		push.messageUpdated(access.audience(c), c.getId(), m.getId());
	}

	// ---- biểu cảm ----

	/** Thả biểu cảm vào tin của người khác. Thả lại cùng emoji thì không đổi gì. */
	public MessageView react(Long messageId, String emoji) {
		Long me = access.me();
		ChatMessage m = load(messageId);
		Conversation c = access.requireAccess(m.getConversationId(), me);
		access.requireCanSend(c);
		if (m.isDeleted()) {
			throw new NotFoundException("Không tìm thấy tin nhắn " + messageId);
		}
		// Giống phần bình luận của app: biểu cảm là phản hồi của người khác, không tự thả vào tin của mình
		if (m.getAuthorId().equals(me)) {
			throw new ForbiddenException(ErrorCode.SELF_REACTION);
		}
		String clean = Emoji.require(emoji);
		if (reactions.findByMessageIdAndUserIdAndEmoji(messageId, me, clean).isEmpty()) {
			if (!reactions.existsByMessageIdAndEmoji(messageId, clean)
					&& reactions.countDistinctEmoji(messageId) >= ChatReaction.MAX_DISTINCT) {
				throw new ValidationException(ErrorCode.TOO_MANY_REACTIONS, ErrorCode.TOO_MANY_REACTIONS.defaultMessage());
			}
			reactions.saveAndFlush(new ChatReaction(messageId, me, clean, clock.instant()));
			push.messageUpdated(access.audience(c), c.getId(), messageId);
		}
		return views(List.of(m), me).get(0);
	}

	/** Gỡ biểu cảm của chính mình. Gỡ cái chưa thả thì không đổi gì. */
	public MessageView unreact(Long messageId, String emoji) {
		Long me = access.me();
		ChatMessage m = load(messageId);
		Conversation c = access.requireAccess(m.getConversationId(), me);
		access.requireCanSend(c);
		var existing = reactions.findByMessageIdAndUserIdAndEmoji(messageId, me, emoji);
		if (existing.isPresent()) {
			reactions.delete(existing.get());
			reactions.flush();
			push.messageUpdated(access.audience(c), c.getId(), messageId);
		}
		return views(List.of(m), me).get(0);
	}

	// ---- đính kèm ----

	/** Tải file đính kèm: người trong cuộc trò chuyện tải được; file của tin đã xoá thì không. */
	@Transactional(readOnly = true)
	public Download download(Long messageId, Long attachmentId) {
		Long me = access.me();
		ChatMessage m = load(messageId);
		access.requireAccess(m.getConversationId(), me);
		if (m.isDeleted()) {
			throw new NotFoundException("Không tìm thấy file " + attachmentId);
		}
		ChatAttachment file = attachments.findByIdAndMessageId(attachmentId, messageId)
				.orElseThrow(() -> new NotFoundException("Không tìm thấy file " + attachmentId));
		try {
			return new Download(file.getFileName(), file.getContentType(), file.getSizeBytes(),
					storage.load(file.getStorageKey()));
		} catch (IOException e) {
			throw new NotFoundException("File không còn trên máy chủ");
		}
	}

	// ---- nội bộ ----

	private ChatMessage load(Long id) {
		return messages.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy tin nhắn " + id));
	}

	private void requireAuthor(ChatMessage m, Long me) {
		if (!m.getAuthorId().equals(me)) {
			throw new ForbiddenException("Chỉ tác giả được sửa hoặc xoá tin nhắn của mình");
		}
	}

	private List<Prepared> prepare(List<NewFile> files) {
		List<Prepared> result = new ArrayList<>();
		for (NewFile file : files) {
			String name = uploadPolicy.safeName(file.originalName());
			try {
				BufferedInputStream in = new BufferedInputStream(file.content());
				in.mark(UploadPolicy.HEAD_BYTES);
				byte[] head = in.readNBytes(UploadPolicy.HEAD_BYTES);
				in.reset();
				uploadPolicy.validate(name, file.sizeBytes(), head);
				result.add(new Prepared(name, file.sizeBytes(), in));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return result;
	}

	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		} catch (IOException ignored) {
			// Dọn dẹp thất bại không che lỗi gốc.
		}
	}

	/** Dựng góc nhìn của {@code me} cho một loạt tin (tải gộp file, biểu cảm, tin được trích dẫn, tên người). */
	List<MessageView> views(List<ChatMessage> rows, Long me) {
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> ids = rows.stream().filter(m -> !m.isDeleted()).map(ChatMessage::getId).toList();
		Map<Long, List<AttachmentView>> filesByMessage = ids.isEmpty() ? Map.of()
				: attachments.findByMessageIdInOrderByIdAsc(ids).stream()
						.collect(Collectors.groupingBy(ChatAttachment::getMessageId, LinkedHashMap::new,
								Collectors.mapping(a -> new AttachmentView(a.getId(), a.getFileName(), a.getContentType(),
										a.getSizeBytes(), a.isImage()), Collectors.toList())));
		Map<Long, List<ChatReaction>> reactionsByMessage = ids.isEmpty() ? Map.of()
				: reactions.findByMessageIdInOrderByIdAsc(ids).stream()
						.collect(Collectors.groupingBy(ChatReaction::getMessageId, LinkedHashMap::new, Collectors.toList()));

		Set<Long> replyIds = rows.stream().map(ChatMessage::getReplyToId).filter(java.util.Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, ChatMessage> replies = replyIds.isEmpty() ? Map.of()
				: messages.findAllByIds(replyIds).stream().collect(Collectors.toMap(ChatMessage::getId, m -> m));

		Set<Long> personIds = new HashSet<>();
		rows.forEach(m -> personIds.add(m.getAuthorId()));
		replies.values().forEach(m -> personIds.add(m.getAuthorId()));
		reactionsByMessage.values().forEach(list -> list.forEach(r -> personIds.add(r.getUserId())));
		Map<Long, PersonView> people = access.people(personIds);

		List<MessageView> result = new ArrayList<>();
		for (ChatMessage m : rows) {
			ReplyRef reply = null;
			if (m.getReplyToId() != null) {
				ChatMessage target = replies.get(m.getReplyToId());
				if (target != null) {
					reply = new ReplyRef(target.getId(), people.get(target.getAuthorId()),
							target.isDeleted() ? null : ConversationService.snippet(target.getBody()), target.isDeleted());
				}
			}
			if (m.isDeleted()) {
				result.add(new MessageView(m.getId(), m.getConversationId(), people.get(m.getAuthorId()), null, true, reply,
						List.of(), List.of(), m.getCreatedAt(), null));
			} else {
				result.add(new MessageView(m.getId(), m.getConversationId(), people.get(m.getAuthorId()), m.getBody(),
						false, reply, filesByMessage.getOrDefault(m.getId(), List.of()),
						reactionViews(reactionsByMessage.getOrDefault(m.getId(), List.of()), people, me), m.getCreatedAt(),
						m.getEditedAt()));
			}
		}
		return result;
	}

	private static List<ReactionView> reactionViews(Collection<ChatReaction> list, Map<Long, PersonView> people, Long me) {
		Map<String, List<ChatReaction>> byEmoji = list.stream()
				.collect(Collectors.groupingBy(ChatReaction::getEmoji, LinkedHashMap::new, Collectors.toList()));
		List<ReactionView> views = new ArrayList<>();
		byEmoji.forEach((emoji, rs) -> views.add(new ReactionView(emoji, rs.size(),
				rs.stream().anyMatch(r -> r.getUserId().equals(me)),
				rs.stream().map(r -> people.get(r.getUserId())).filter(p -> p != null).map(PersonView::fullName).toList())));
		return views;
	}
}
