package com.soudan.task.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.task.TaskCommentReactedEvent;
import com.soudan.task.TaskCommentedEvent;
import com.soudan.task.domain.CommentReaction;
import com.soudan.task.domain.Task;
import com.soudan.task.domain.TaskComment;
import com.soudan.task.repository.CommentReactionRepository;
import com.soudan.task.repository.TaskCommentRepository;
import com.soudan.task.repository.TaskRepository;
import com.soudan.task.service.TaskViews.CommentView;
import com.soudan.task.service.TaskViews.PersonView;
import com.soudan.task.service.TaskViews.ReactionView;
import com.soudan.user.UserApi;
import com.soudan.user.UserSummary;

/**
 * Bình luận trên task: viết, trả lời (một cấp), sửa (chỉ tác giả), xoá (tác giả hoặc Leader) và thả biểu cảm.
 * Ai xem được task (Admin và mọi người có mặt trong nhóm) thì đọc, viết, trả lời và thả biểu cảm được.
 * Bình luận gốc đã xoá nhưng còn trả lời vẫn được trả ra dạng "đã xoá" (không có nội dung) để các trả lời có chỗ bám.
 */
@Service
@Transactional
public class TaskCommentService {

	private final TaskRepository tasks;
	private final TaskCommentRepository comments;
	private final CommentReactionRepository reactions;
	private final UserApi users;
	private final AccessPolicy access;
	private final CurrentUser currentUser;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public TaskCommentService(TaskRepository tasks, TaskCommentRepository comments, CommentReactionRepository reactions,
			UserApi users, AccessPolicy access, CurrentUser currentUser, Clock clock, ApplicationEventPublisher events) {
		this.tasks = tasks;
		this.comments = comments;
		this.reactions = reactions;
		this.users = users;
		this.access = access;
		this.currentUser = currentUser;
		this.clock = clock;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public List<CommentView> list(Long taskId) {
		requireCanView(load(taskId));
		return buildAll(taskId);
	}

	/**
	 * Viết bình luận mới. {@code replyToId} là bình luận được trả lời (có thể là một trả lời khác: khi đó bình luận
	 * mới gắn vào bình luận gốc của chuỗi, nhưng người được báo vẫn là tác giả của bình luận được trả lời).
	 */
	public CommentView add(Long taskId, String body, Long replyToId) {
		Task task = load(taskId);
		requireCanView(task);
		Long parentId = null;
		Long repliedToAuthor = null;
		if (replyToId != null) {
			TaskComment target = live(taskId, replyToId);
			repliedToAuthor = target.getAuthorId();
			parentId = target.getParentId() != null ? target.getParentId() : target.getId();
		}
		Long me = currentUser.require().id();
		TaskComment saved = comments.saveAndFlush(TaskComment.create(taskId, me, parentId, body));
		events.publishEvent(new TaskCommentedEvent(taskId, task.getTeamId(), task.getTitle(), saved.getId(), me,
				task.getAssigneeId(), repliedToAuthor, saved.getBody()));
		return viewOf(taskId, saved.getId());
	}

	/** Chỉ tác giả sửa được bình luận của mình (kể cả Leader cũng không sửa lời người khác). */
	public CommentView edit(Long taskId, Long commentId, String body) {
		requireCanView(load(taskId));
		TaskComment comment = live(taskId, commentId);
		if (!comment.getAuthorId().equals(currentUser.require().id())) {
			throw new ForbiddenException("Chỉ tác giả được sửa bình luận");
		}
		comment.edit(body, clock.instant());
		comments.saveAndFlush(comment);
		return viewOf(taskId, commentId);
	}

	public void delete(Long taskId, Long commentId) {
		Task task = load(taskId);
		requireCanView(task);
		TaskComment comment = live(taskId, commentId);
		boolean author = comment.getAuthorId().equals(currentUser.require().id());
		if (!author && !access.isTeamLeader(task.getTeamId())) {
			throw new ForbiddenException("Chỉ tác giả hoặc Leader của nhóm được xoá bình luận");
		}
		comment.markDeleted(clock.instant());
		comments.saveAndFlush(comment);
	}

	/** Thả biểu cảm vào bình luận của người khác. Thả lại cùng emoji thì không đổi gì (idempotent). */
	public CommentView react(Long taskId, Long commentId, String emoji) {
		Task task = load(taskId);
		requireCanView(task);
		TaskComment comment = live(taskId, commentId);
		Long me = currentUser.require().id();
		// Không tự thả biểu cảm vào bình luận của mình (giống mạng xã hội: biểu cảm là phản hồi của người khác)
		if (comment.getAuthorId().equals(me)) {
			throw new ForbiddenException(ErrorCode.SELF_REACTION);
		}
		String clean = CommentReaction.requireEmoji(emoji);
		if (reactions.findByCommentIdAndUserIdAndEmoji(commentId, me, clean).isEmpty()) {
			boolean newKind = !reactions.existsByCommentIdAndEmoji(commentId, clean);
			if (newKind && reactions.countDistinctEmoji(commentId) >= CommentReaction.MAX_DISTINCT_PER_COMMENT) {
				throw new ValidationException(ErrorCode.TOO_MANY_REACTIONS, ErrorCode.TOO_MANY_REACTIONS.defaultMessage());
			}
			reactions.saveAndFlush(new CommentReaction(commentId, me, clean, clock.instant()));
			events.publishEvent(new TaskCommentReactedEvent(taskId, task.getTeamId(), task.getTitle(), commentId,
					comment.getAuthorId(), me, clean));
		}
		return viewOf(taskId, commentId);
	}

	/** Gỡ biểu cảm của chính mình. Gỡ cái chưa thả thì không đổi gì. */
	public CommentView unreact(Long taskId, Long commentId, String emoji) {
		requireCanView(load(taskId));
		live(taskId, commentId);
		Long me = currentUser.require().id();
		reactions.findByCommentIdAndUserIdAndEmoji(commentId, me, emoji).ifPresent(reactions::delete);
		reactions.flush();
		return viewOf(taskId, commentId);
	}

	// ---- nội bộ ----

	private Task load(Long taskId) {
		return tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Không tìm thấy task " + taskId));
	}

	/** Bình luận còn sống của đúng task này; đã xoá hoặc thuộc task khác thì coi như không tồn tại. */
	private TaskComment live(Long taskId, Long commentId) {
		return comments.findByIdAndTaskId(commentId, taskId)
				.filter(c -> !c.isDeleted())
				.orElseThrow(() -> new NotFoundException("Không tìm thấy bình luận " + commentId));
	}

	private void requireCanView(Task task) {
		if (!access.canViewTeam(task.getTeamId())) {
			throw new ForbiddenException("Bạn không thuộc nhóm này");
		}
	}

	private CommentView viewOf(Long taskId, Long commentId) {
		return buildAll(taskId).stream().filter(v -> v.id().equals(commentId)).findFirst()
				.orElseThrow(() -> new NotFoundException("Không tìm thấy bình luận " + commentId));
	}

	private List<CommentView> buildAll(Long taskId) {
		List<TaskComment> rows = comments.findByTaskIdOrderByCreatedAtAscIdAsc(taskId);
		Set<Long> parentsWithLiveReplies = rows.stream()
				.filter(c -> !c.isDeleted() && c.getParentId() != null)
				.map(TaskComment::getParentId)
				.collect(Collectors.toSet());
		List<TaskComment> visible = rows.stream()
				.filter(c -> !c.isDeleted() || parentsWithLiveReplies.contains(c.getId()))
				.toList();
		List<Long> liveIds = visible.stream().filter(c -> !c.isDeleted()).map(TaskComment::getId).toList();

		Map<Long, List<CommentReaction>> byComment = liveIds.isEmpty() ? Map.of()
				: reactions.findByCommentIdInOrderByIdAsc(liveIds).stream()
						.collect(Collectors.groupingBy(CommentReaction::getCommentId, LinkedHashMap::new, Collectors.toList()));

		Set<Long> personIds = new HashSet<>();
		visible.stream().filter(c -> !c.isDeleted()).forEach(c -> personIds.add(c.getAuthorId()));
		byComment.values().forEach(list -> list.forEach(r -> personIds.add(r.getUserId())));
		Map<Long, PersonView> people = users.findSummariesByIds(personIds).stream()
				.collect(Collectors.toMap(UserSummary::id, u -> new PersonView(u.id(), u.fullName(), u.active())));

		Long me = currentUser.require().id();
		List<CommentView> result = new ArrayList<>();
		for (TaskComment c : visible) {
			if (c.isDeleted()) {
				result.add(new CommentView(c.getId(), c.getParentId(), null, null, true, c.getCreatedAt(), null, List.of()));
				continue;
			}
			result.add(new CommentView(c.getId(), c.getParentId(), people.get(c.getAuthorId()), c.getBody(), false,
					c.getCreatedAt(), c.getEditedAt(), reactionViews(byComment.getOrDefault(c.getId(), List.of()), people, me)));
		}
		return result;
	}

	private static List<ReactionView> reactionViews(List<CommentReaction> list, Map<Long, PersonView> people, Long me) {
		Map<String, List<CommentReaction>> byEmoji = list.stream()
				.collect(Collectors.groupingBy(CommentReaction::getEmoji, LinkedHashMap::new, Collectors.toList()));
		List<ReactionView> views = new ArrayList<>();
		byEmoji.forEach((emoji, rs) -> views.add(new ReactionView(emoji, rs.size(),
				rs.stream().anyMatch(r -> r.getUserId().equals(me)),
				rs.stream().map(r -> people.get(r.getUserId())).filter(p -> p != null).map(PersonView::fullName).toList())));
		return views;
	}
}
