package com.soudan.report.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.file.FileStorage;
import com.soudan.common.file.UploadPolicy;
import com.soudan.common.util.Emoji;
import com.soudan.common.util.RichText;
import com.soudan.report.domain.Community;
import com.soudan.report.ReportCommentedEvent;
import com.soudan.report.ReportPostedEvent;
import com.soudan.report.ReportReactedEvent;
import com.soudan.report.domain.ReportAttachment;
import com.soudan.report.domain.ReportComment;
import com.soudan.report.domain.ReportPost;
import com.soudan.report.domain.ReportReaction;
import com.soudan.report.repository.ReportAttachmentRepository;
import com.soudan.report.repository.ReportCommentRepository;
import com.soudan.report.repository.ReportCommentRepository.CommentCount;
import com.soudan.report.repository.ReportPostRepository;
import com.soudan.report.repository.ReportReactionRepository;
import com.soudan.report.service.ReportViews.AttachmentView;
import com.soudan.report.service.ReportViews.CommentView;
import com.soudan.report.service.ReportViews.PersonView;
import com.soudan.report.service.ReportViews.PostView;
import com.soudan.report.service.ReportViews.ReactionView;

/**
 * Báo cáo hằng ngày trong cộng đồng. Mỗi thành viên có trang báo cáo riêng; mọi người trong cộng đồng (và chủ
 * cộng đồng) xem được từng trang, bình luận, trả lời một cấp và thả biểu cảm, nhưng chỉ chính người đó đăng và sửa. Quy tắc:
 * <ul>
 * <li>Chỉ thành viên được thêm vào mới nộp báo cáo; mỗi người một báo cáo mỗi ngày, nộp lại trong ngày thì cập nhật.</li>
 * <li>"Ngày" tính theo múi giờ cấu hình; báo cáo của ngày trước không sửa được.</li>
 * <li>Sửa bình luận: chỉ tác giả. Xoá bình luận: tác giả hoặc chủ cộng đồng.</li>
 * <li>Không tự thả biểu cảm vào báo cáo hay bình luận của chính mình.</li>
 * </ul>
 */
@Service
@Transactional
public class ReportService {

	public static final int MAX_PAGE_SIZE = 50;

	private final ReportPostRepository posts;
	private final ReportCommentRepository comments;
	private final ReportReactionRepository reactions;
	private final ReportAttachmentRepository attachments;
	private final FileStorage storage;
	private final UploadPolicy uploadPolicy;
	private final CommunityAccess access;
	private final CommunityService communityService;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public ReportService(ReportPostRepository posts, ReportCommentRepository comments,
			ReportReactionRepository reactions, ReportAttachmentRepository attachments, FileStorage storage,
			UploadPolicy uploadPolicy, CommunityAccess access, CommunityService communityService, Clock clock,
			ApplicationEventPublisher events) {
		this.posts = posts;
		this.comments = comments;
		this.reactions = reactions;
		this.attachments = attachments;
		this.storage = storage;
		this.uploadPolicy = uploadPolicy;
		this.access = access;
		this.communityService = communityService;
		this.clock = clock;
		this.events = events;
	}

	// ---- báo cáo ----

	/** Một file người dùng vừa chọn, chưa lưu. Người gọi đóng {@code content}. */
	public record NewFile(String originalName, long sizeBytes, InputStream content) {
	}

	/** Một file đã kiểm tra, sẵn sàng lưu (luồng đã đọc đầu file nên có thể đọc lại từ đầu). */
	private record Prepared(String name, long size, BufferedInputStream content) {
	}

	/**
	 * Nộp báo cáo hôm nay; đã nộp rồi thì cập nhật bản cũ. Một yêu cầu gồm tiêu đề, nội dung, file mới và danh sách
	 * file cũ cần gỡ. Mọi file được kiểm tra (đuôi, kích thước, nội dung) trước khi lưu bất cứ thứ gì; báo cáo phải có
	 * nội dung chữ hoặc ít nhất một file đính kèm.
	 */
	public PostView submit(Long communityId, String title, String body, List<NewFile> files, List<Long> removeIds) {
		Community community = access.requireMember(communityId);
		Long me = access.me();
		LocalDate today = communityService.today();
		List<NewFile> newFiles = files == null ? List.of() : files;
		List<Prepared> prepared = prepare(newFiles);
		List<String> storedKeys = new ArrayList<>();
		try {
			ReportPost post = posts.findOfDay(communityId, me, today).orElse(null);
			boolean created = post == null;
			if (created) {
				post = ReportPost.create(communityId, me, today, title, body);
			} else {
				post.update(title, body);
			}
			ReportPost saved = posts.saveAndFlush(post);

			Set<Long> toRemove = removeIds == null ? Set.of() : new HashSet<>(removeIds);
			List<ReportAttachment> existing = attachments.findByPostIdOrderByIdAsc(saved.getId());
			List<ReportAttachment> removed = existing.stream().filter(a -> toRemove.contains(a.getId())).toList();
			int remaining = existing.size() - removed.size();
			if (remaining + prepared.size() > ReportAttachment.MAX_PER_POST) {
				throw new ValidationException(ErrorCode.TOO_MANY_ATTACHMENTS, ErrorCode.TOO_MANY_ATTACHMENTS.defaultMessage());
			}
			if (!saved.hasText() && remaining + prepared.size() == 0) {
				throw new ValidationException("Hãy nhập nội dung hoặc đính kèm ít nhất một file");
			}

			attachments.deleteAll(removed);
			for (Prepared file : prepared) {
				String key = storage.save(file.content());
				storedKeys.add(key);
				attachments.save(new ReportAttachment(saved.getId(), file.name(), uploadPolicy.contentTypeOf(file.name()),
						file.size(), key, me, clock.instant()));
			}
			attachments.flush();
			removed.forEach(a -> deleteQuietly(a.getStorageKey()));
			if (created) {
				events.publishEvent(new ReportPostedEvent(communityId, saved.getId(), me, community.getOwnerId(),
						saved.getTitle()));
			}
			return buildPosts(List.of(saved)).get(0);
		} catch (DataIntegrityViolationException e) {
			storedKeys.forEach(this::deleteQuietly);
			// Hai request cùng lúc cùng người, cùng ngày: người sau nhận xung đột thay vì lỗi 500
			throw new ConflictException(ErrorCode.CONFLICT);
		} catch (IOException e) {
			storedKeys.forEach(this::deleteQuietly);
			throw new UncheckedIOException(e);
		} catch (RuntimeException e) {
			storedKeys.forEach(this::deleteQuietly);
			throw e;
		}
	}

	/** Một file đã mở để tải; người gọi đóng {@code content}. */
	public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
	}

	/** Tải file đính kèm: mọi người trong cộng đồng (và chủ cộng đồng) tải được. */
	@Transactional(readOnly = true)
	public Download download(Long postId, Long attachmentId) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		ReportAttachment file = attachments.findByIdAndPostId(attachmentId, postId)
				.orElseThrow(() -> new NotFoundException("Không tìm thấy file " + attachmentId));
		try {
			return new Download(file.getFileName(), file.getContentType(), file.getSizeBytes(),
					storage.load(file.getStorageKey()));
		} catch (IOException e) {
			throw new NotFoundException("File không còn trên máy chủ");
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

	/** Báo cáo hôm nay của chính mình (để form nạp lại nội dung đã nộp). */
	@Transactional(readOnly = true)
	public Optional<PostView> myToday(Long communityId) {
		access.requireMember(communityId);
		return posts.findOfDay(communityId, access.me(), communityService.today())
				.map(p -> buildPosts(List.of(p)).get(0));
	}

	/**
	 * Báo cáo của MỘT thành viên, ngày mới nhất trước. Báo cáo không để chung: người trong cộng đồng chỉ xem được
	 * từng người một (mở nhãn tên của người đó). Người không còn là thành viên thì không có trang báo cáo.
	 */
	@Transactional(readOnly = true)
	public Page<PostView> feed(Long communityId, Long authorId, int page, int size) {
		access.requireParticipant(communityId);
		if (!access.hasMember(communityId, authorId)) {
			throw new NotFoundException("Người này không có trong cộng đồng");
		}
		PageRequest paging = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		Page<ReportPost> rows = posts.feedOf(communityId, authorId, paging);
		List<PostView> views = buildPosts(rows.getContent());
		return new org.springframework.data.domain.PageImpl<>(views, paging, rows.getTotalElements());
	}

	@Transactional(readOnly = true)
	public PostView getPost(Long postId) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		return buildPosts(List.of(post)).get(0);
	}

	public PostView reactToPost(Long postId, String emoji) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		Long me = access.me();
		if (post.getAuthorId().equals(me)) {
			throw new ForbiddenException(ErrorCode.SELF_REACTION);
		}
		String clean = Emoji.require(emoji);
		if (reactions.findByPostIdAndUserIdAndEmoji(postId, me, clean).isEmpty()) {
			if (!reactions.existsByPostIdAndEmoji(postId, clean)
					&& reactions.countDistinctOnPost(postId) >= ReportReaction.MAX_DISTINCT) {
				throw new ValidationException(ErrorCode.TOO_MANY_REACTIONS, ErrorCode.TOO_MANY_REACTIONS.defaultMessage());
			}
			reactions.saveAndFlush(ReportReaction.onPost(postId, me, clean, clock.instant()));
			events.publishEvent(new ReportReactedEvent(post.getCommunityId(), postId, post.getTitle(),
					post.getAuthorId(), false, post.getAuthorId(), me, clean));
		}
		return buildPosts(List.of(post)).get(0);
	}

	public PostView unreactPost(Long postId, String emoji) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		reactions.findByPostIdAndUserIdAndEmoji(postId, access.me(), emoji).ifPresent(reactions::delete);
		reactions.flush();
		return buildPosts(List.of(post)).get(0);
	}

	// ---- bình luận ----

	@Transactional(readOnly = true)
	public List<CommentView> listComments(Long postId) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		return buildComments(postId);
	}

	/** {@code replyToId} là bình luận được trả lời; trả lời một trả lời thì gắn vào bình luận gốc của chuỗi. */
	public CommentView addComment(Long postId, String body, Long replyToId) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		Long parentId = null;
		Long repliedToAuthor = null;
		if (replyToId != null) {
			ReportComment target = liveComment(postId, replyToId);
			parentId = target.getParentId() != null ? target.getParentId() : target.getId();
			repliedToAuthor = target.getAuthorId();
		}
		ReportComment saved = comments.saveAndFlush(ReportComment.create(postId, access.me(), parentId, body));
		events.publishEvent(new ReportCommentedEvent(post.getCommunityId(), postId, post.getTitle(), post.getAuthorId(),
				saved.getId(), saved.getAuthorId(), repliedToAuthor, saved.getBody()));
		return commentView(postId, saved.getId());
	}

	public CommentView editComment(Long postId, Long commentId, String body) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		ReportComment comment = liveComment(postId, commentId);
		if (!comment.getAuthorId().equals(access.me())) {
			throw new ForbiddenException("Chỉ tác giả được sửa bình luận");
		}
		comment.edit(body, clock.instant());
		comments.saveAndFlush(comment);
		return commentView(postId, commentId);
	}

	public void deleteComment(Long postId, Long commentId) {
		ReportPost post = livePost(postId);
		Community community = access.requireParticipant(post.getCommunityId());
		ReportComment comment = liveComment(postId, commentId);
		if (!comment.getAuthorId().equals(access.me()) && !access.isOwner(community)) {
			throw new ForbiddenException("Chỉ tác giả hoặc chủ cộng đồng được xoá bình luận");
		}
		comment.markDeleted(clock.instant());
		comments.saveAndFlush(comment);
	}

	public CommentView reactToComment(Long postId, Long commentId, String emoji) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		ReportComment comment = liveComment(postId, commentId);
		Long me = access.me();
		if (comment.getAuthorId().equals(me)) {
			throw new ForbiddenException(ErrorCode.SELF_REACTION);
		}
		String clean = Emoji.require(emoji);
		if (reactions.findByCommentIdAndUserIdAndEmoji(commentId, me, clean).isEmpty()) {
			if (!reactions.existsByCommentIdAndEmoji(commentId, clean)
					&& reactions.countDistinctOnComment(commentId) >= ReportReaction.MAX_DISTINCT) {
				throw new ValidationException(ErrorCode.TOO_MANY_REACTIONS, ErrorCode.TOO_MANY_REACTIONS.defaultMessage());
			}
			reactions.saveAndFlush(ReportReaction.onComment(commentId, me, clean, clock.instant()));
			events.publishEvent(new ReportReactedEvent(post.getCommunityId(), postId, post.getTitle(),
					post.getAuthorId(), true, comment.getAuthorId(), me, clean));
		}
		return commentView(postId, commentId);
	}

	public CommentView unreactComment(Long postId, Long commentId, String emoji) {
		ReportPost post = livePost(postId);
		access.requireParticipant(post.getCommunityId());
		liveComment(postId, commentId);
		reactions.findByCommentIdAndUserIdAndEmoji(commentId, access.me(), emoji).ifPresent(reactions::delete);
		reactions.flush();
		return commentView(postId, commentId);
	}

	// ---- nội bộ ----

	private ReportPost livePost(Long postId) {
		return posts.findLive(postId).orElseThrow(() -> new NotFoundException("Không tìm thấy báo cáo " + postId));
	}

	private ReportComment liveComment(Long postId, Long commentId) {
		return comments.findByIdAndPostId(commentId, postId)
				.filter(c -> !c.isDeleted())
				.orElseThrow(() -> new NotFoundException("Không tìm thấy bình luận " + commentId));
	}

	private CommentView commentView(Long postId, Long commentId) {
		return buildComments(postId).stream().filter(v -> v.id().equals(commentId)).findFirst()
				.orElseThrow(() -> new NotFoundException("Không tìm thấy bình luận " + commentId));
	}

	private List<PostView> buildPosts(List<ReportPost> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> ids = rows.stream().map(ReportPost::getId).toList();
		Map<Long, List<ReportReaction>> byPost = reactions.findByPostIdInOrderByIdAsc(ids).stream()
				.collect(Collectors.groupingBy(ReportReaction::getPostId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, List<AttachmentView>> filesByPost = attachments.findByPostIdInOrderByIdAsc(ids).stream()
				.collect(Collectors.groupingBy(ReportAttachment::getPostId, LinkedHashMap::new,
						Collectors.mapping(a -> new AttachmentView(a.getId(), a.getFileName(), a.getContentType(),
								a.getSizeBytes(), a.isImage()), Collectors.toList())));
		Map<Long, Integer> commentCounts = comments.countLiveByPosts(ids).stream()
				.collect(Collectors.toMap(CommentCount::getPostId, c -> (int) c.getTotal()));

		Set<Long> personIds = new HashSet<>();
		rows.forEach(p -> personIds.add(p.getAuthorId()));
		byPost.values().forEach(list -> list.forEach(r -> personIds.add(r.getUserId())));
		Map<Long, PersonView> people = access.people(personIds);

		LocalDate today = communityService.today();
		Long me = access.me();
		List<PostView> result = new ArrayList<>();
		for (ReportPost p : rows) {
			boolean edited = p.getUpdatedAt() != null && p.getCreatedAt() != null
					&& p.getUpdatedAt().isAfter(p.getCreatedAt().plusSeconds(1));
			result.add(new PostView(p.getId(), p.getCommunityId(), people.get(p.getAuthorId()), p.getReportDate(),
					p.getTitle(), p.getBody() == null ? "" : p.getBody(),
					filesByPost.getOrDefault(p.getId(), List.of()),
					p.getAuthorId().equals(me) && p.isEditableOn(today), edited, p.getCreatedAt(), p.getVersion(),
					commentCounts.getOrDefault(p.getId(), 0),
					reactionViews(byPost.getOrDefault(p.getId(), List.of()), people, me)));
		}
		return result;
	}

	private List<CommentView> buildComments(Long postId) {
		List<ReportComment> rows = comments.findByPostIdOrderByCreatedAtAscIdAsc(postId);
		Set<Long> parentsWithLiveReplies = rows.stream()
				.filter(c -> !c.isDeleted() && c.getParentId() != null)
				.map(ReportComment::getParentId)
				.collect(Collectors.toSet());
		List<ReportComment> visible = rows.stream()
				.filter(c -> !c.isDeleted() || parentsWithLiveReplies.contains(c.getId()))
				.toList();
		List<Long> liveIds = visible.stream().filter(c -> !c.isDeleted()).map(ReportComment::getId).toList();

		Map<Long, List<ReportReaction>> byComment = liveIds.isEmpty() ? Map.of()
				: reactions.findByCommentIdInOrderByIdAsc(liveIds).stream()
						.collect(Collectors.groupingBy(ReportReaction::getCommentId, LinkedHashMap::new, Collectors.toList()));

		Set<Long> personIds = new HashSet<>();
		visible.stream().filter(c -> !c.isDeleted()).forEach(c -> personIds.add(c.getAuthorId()));
		byComment.values().forEach(list -> list.forEach(r -> personIds.add(r.getUserId())));
		Map<Long, PersonView> people = access.people(personIds);

		Long me = access.me();
		List<CommentView> result = new ArrayList<>();
		for (ReportComment c : visible) {
			if (c.isDeleted()) {
				result.add(new CommentView(c.getId(), c.getParentId(), null, null, true, c.getCreatedAt(), null, List.of()));
				continue;
			}
			result.add(new CommentView(c.getId(), c.getParentId(), people.get(c.getAuthorId()), c.getBody(), false,
					c.getCreatedAt(), c.getEditedAt(), reactionViews(byComment.getOrDefault(c.getId(), List.of()), people, me)));
		}
		return result;
	}

	private static List<ReactionView> reactionViews(List<ReportReaction> list, Map<Long, PersonView> people, Long me) {
		Map<String, List<ReportReaction>> byEmoji = list.stream()
				.collect(Collectors.groupingBy(ReportReaction::getEmoji, LinkedHashMap::new, Collectors.toList()));
		List<ReactionView> views = new ArrayList<>();
		byEmoji.forEach((emoji, rs) -> views.add(new ReactionView(emoji, rs.size(),
				rs.stream().anyMatch(r -> r.getUserId().equals(me)),
				rs.stream().map(r -> people.get(r.getUserId())).filter(p -> p != null).map(PersonView::fullName).toList())));
		return views;
	}
}
