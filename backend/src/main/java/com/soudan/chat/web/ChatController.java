package com.soudan.chat.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.soudan.chat.domain.ConversationType;
import com.soudan.chat.domain.DirectStatus;
import com.soudan.chat.service.ChatViews.AttachmentView;
import com.soudan.chat.service.ChatViews.ConversationView;
import com.soudan.chat.service.ChatViews.LastMessage;
import com.soudan.chat.service.ChatViews.MessagePage;
import com.soudan.chat.service.ChatViews.MessageView;
import com.soudan.chat.service.ChatViews.PersonView;
import com.soudan.chat.service.ChatViews.ReactionView;
import com.soudan.chat.service.ChatViews.ReplyRef;
import com.soudan.chat.service.ChatViews.TeamRef;
import com.soudan.chat.service.ChatViews.UnreadEntry;
import com.soudan.chat.service.ChatViews.UnreadSummary;
import com.soudan.chat.service.ConversationService;
import com.soudan.chat.service.MessageService;
import com.soudan.chat.service.MessageService.NewFile;
import com.soudan.common.api.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Trò chuyện: nhắn riêng (cần đối phương chấp nhận lời mời) và phòng chat của nhóm. Quyền kiểm tra trong service;
 * Admin bị chặn ở mọi thao tác. Thời gian thực đi qua WebSocket (xem WebSocketConfig), REST dùng cho mọi thao tác ghi.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

	// ---- request / response ----

	public record StartDirectRequest(@NotNull Long userId) {
	}

	public record MessageRequest(@NotBlank @Size(max = 4000) String body) {
	}

	public record ReactionRequest(@NotBlank @Size(max = 32) String emoji) {
	}

	public record PersonResponse(Long id, String fullName, String position) {
		static PersonResponse of(PersonView v) {
			return v == null ? null : new PersonResponse(v.id(), v.fullName(), v.position());
		}
	}

	public record TeamResponse(Long id, String name, boolean active) {
		static TeamResponse of(TeamRef v) {
			return v == null ? null : new TeamResponse(v.id(), v.name(), v.active());
		}
	}

	public record LastMessageResponse(Long id, Long authorId, String authorName, String snippet, boolean deleted,
			boolean hasAttachment, Instant at) {
		static LastMessageResponse of(LastMessage v) {
			return v == null ? null : new LastMessageResponse(v.id(), v.authorId(), v.authorName(), v.snippet(),
					v.deleted(), v.hasAttachment(), v.at());
		}
	}

	public record ConversationResponse(Long id, ConversationType type, DirectStatus status, boolean requestedByMe,
			boolean canSend, PersonResponse other, TeamResponse team, LastMessageResponse last, long unread,
			Instant createdAt) {
		static ConversationResponse of(ConversationView v) {
			return new ConversationResponse(v.id(), v.type(), v.status(), v.requestedByMe(), v.canSend(),
					PersonResponse.of(v.other()), TeamResponse.of(v.team()), LastMessageResponse.of(v.last()), v.unread(),
					v.createdAt());
		}
	}

	public record AttachmentResponse(Long id, String fileName, String contentType, long sizeBytes, boolean image) {
		static AttachmentResponse of(AttachmentView v) {
			return new AttachmentResponse(v.id(), v.fileName(), v.contentType(), v.sizeBytes(), v.image());
		}
	}

	public record ReactionResponse(String emoji, int count, boolean reactedByMe, List<String> users) {
		static ReactionResponse of(ReactionView v) {
			return new ReactionResponse(v.emoji(), v.count(), v.reactedByMe(), v.users());
		}
	}

	public record ReplyResponse(Long id, PersonResponse author, String snippet, boolean deleted) {
		static ReplyResponse of(ReplyRef v) {
			return v == null ? null : new ReplyResponse(v.id(), PersonResponse.of(v.author()), v.snippet(), v.deleted());
		}
	}

	public record MessageResponse(Long id, Long conversationId, PersonResponse author, String body, boolean deleted,
			ReplyResponse replyTo, List<AttachmentResponse> attachments, List<ReactionResponse> reactions,
			Instant createdAt, Instant editedAt) {
		public static MessageResponse of(MessageView v) {
			return new MessageResponse(v.id(), v.conversationId(), PersonResponse.of(v.author()), v.body(), v.deleted(),
					ReplyResponse.of(v.replyTo()), v.attachments().stream().map(AttachmentResponse::of).toList(),
					v.reactions().stream().map(ReactionResponse::of).toList(), v.createdAt(), v.editedAt());
		}
	}

	public record MessagePageResponse(List<MessageResponse> items, boolean hasMore) {
		static MessagePageResponse of(MessagePage v) {
			return new MessagePageResponse(v.items().stream().map(MessageResponse::of).toList(), v.hasMore());
		}
	}

	public record UnreadEntryResponse(Long conversationId, Long teamId, long count) {
		static UnreadEntryResponse of(UnreadEntry v) {
			return new UnreadEntryResponse(v.conversationId(), v.teamId(), v.count());
		}
	}

	public record UnreadResponse(long total, List<UnreadEntryResponse> conversations) {
		static UnreadResponse of(UnreadSummary v) {
			return new UnreadResponse(v.total(), v.conversations().stream().map(UnreadEntryResponse::of).toList());
		}
	}

	private final ConversationService conversations;
	private final MessageService messages;

	public ChatController(ConversationService conversations, MessageService messages) {
		this.conversations = conversations;
		this.messages = messages;
	}

	// ---- cuộc trò chuyện ----

	/** Các cuộc nhắn riêng của mình, kể cả lời mời đang chờ. */
	@GetMapping("/conversations")
	public ApiResponse<List<ConversationResponse>> list() {
		return ApiResponse.ok(conversations.listDirect().stream().map(ConversationResponse::of).toList());
	}

	@GetMapping("/conversations/{id}")
	public ApiResponse<ConversationResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(ConversationResponse.of(conversations.get(id)));
	}

	/** Cuộc nhắn riêng với một người (mọi trạng thái); data là null nếu chưa từng mời nhau. */
	@GetMapping("/direct/{userId}")
	public ApiResponse<ConversationResponse> directWith(@PathVariable Long userId) {
		return ApiResponse.ok(conversations.directWith(userId).map(ConversationResponse::of).orElse(null));
	}

	/** Bấm "Nhắn tin" ở hồ sơ một người: gửi lời mời (hoặc mở cuộc trò chuyện nếu đã chấp nhận). */
	@PostMapping("/direct")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ConversationResponse> startDirect(@Valid @RequestBody StartDirectRequest request) {
		return ApiResponse.ok(ConversationResponse.of(conversations.startDirect(request.userId())));
	}

	@PostMapping("/conversations/{id}/accept")
	public ApiResponse<ConversationResponse> accept(@PathVariable Long id) {
		return ApiResponse.ok(ConversationResponse.of(conversations.accept(id)));
	}

	@PostMapping("/conversations/{id}/decline")
	public ApiResponse<ConversationResponse> decline(@PathVariable Long id) {
		return ApiResponse.ok(ConversationResponse.of(conversations.decline(id)));
	}

	/** Người gửi thu hồi lời mời đang chờ. */
	@DeleteMapping("/conversations/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@PathVariable Long id) {
		conversations.cancel(id);
	}

	/** Phòng chat của một nhóm (tạo ở lần đầu mở). */
	@GetMapping("/teams/{teamId}/conversation")
	public ApiResponse<ConversationResponse> teamConversation(@PathVariable Long teamId) {
		return ApiResponse.ok(ConversationResponse.of(conversations.teamConversation(teamId)));
	}

	@GetMapping("/unread")
	public ApiResponse<UnreadResponse> unread() {
		return ApiResponse.ok(UnreadResponse.of(conversations.unread()));
	}

	@PostMapping("/conversations/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markRead(@PathVariable Long id) {
		messages.markRead(id);
	}

	// ---- tin nhắn ----

	/** Lịch sử, mới nhất trước. {@code before} là id tin để lấy các tin cũ hơn (cuộn lên xem thêm). */
	@GetMapping("/conversations/{id}/messages")
	public ApiResponse<MessagePageResponse> history(@PathVariable Long id,
			@RequestParam(required = false) Long before,
			@RequestParam(defaultValue = "" + MessageService.DEFAULT_PAGE) int size) {
		return ApiResponse.ok(MessagePageResponse.of(messages.list(id, before, size)));
	}

	/** Gửi một tin. multipart/form-data: {@code body} (chữ), {@code replyToId}, {@code files} (0..10 file/ảnh). */
	@PostMapping(path = "/conversations/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MessageResponse> send(@PathVariable Long id,
			@RequestParam(value = "body", required = false) String body,
			@RequestParam(value = "replyToId", required = false) Long replyToId,
			@RequestParam(value = "files", required = false) List<MultipartFile> files) throws IOException {
		List<NewFile> uploads = new ArrayList<>();
		if (files != null) {
			for (MultipartFile f : files) {
				if (!f.isEmpty()) {
					uploads.add(new NewFile(f.getOriginalFilename(), f.getSize(), f.getInputStream()));
				}
			}
		}
		return ApiResponse.ok(MessageResponse.of(messages.send(id, body, replyToId, uploads)));
	}

	@GetMapping("/messages/{messageId}")
	public ApiResponse<MessageResponse> message(@PathVariable Long messageId) {
		return ApiResponse.ok(MessageResponse.of(messages.get(messageId)));
	}

	@PutMapping("/messages/{messageId}")
	public ApiResponse<MessageResponse> edit(@PathVariable Long messageId, @Valid @RequestBody MessageRequest request) {
		return ApiResponse.ok(MessageResponse.of(messages.edit(messageId, request.body())));
	}

	@DeleteMapping("/messages/{messageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long messageId) {
		messages.delete(messageId);
	}

	@PutMapping("/messages/{messageId}/reactions")
	public ApiResponse<MessageResponse> react(@PathVariable Long messageId, @Valid @RequestBody ReactionRequest request) {
		return ApiResponse.ok(MessageResponse.of(messages.react(messageId, request.emoji())));
	}

	@DeleteMapping("/messages/{messageId}/reactions")
	public ApiResponse<MessageResponse> unreact(@PathVariable Long messageId, @RequestParam String emoji) {
		return ApiResponse.ok(MessageResponse.of(messages.unreact(messageId, emoji)));
	}

	@GetMapping("/messages/{messageId}/attachments/{attachmentId}/download")
	public ResponseEntity<InputStreamResource> download(@PathVariable Long messageId, @PathVariable Long attachmentId) {
		var file = messages.download(messageId, attachmentId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.sizeBytes())
				.body(new InputStreamResource(file.content()));
	}
}
