package com.soudan.task.domain;

import java.time.LocalDate;

import org.hibernate.annotations.SQLRestriction;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.base.BaseEntity;
import com.soudan.common.exception.BusinessException;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.ValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Task và máy trạng thái của nó. Quy tắc "từ trạng thái nào sang trạng thái nào" nằm hết ở đây;
 * quy tắc "ai được làm" nằm ở TaskService. Không có setter cho {@code status}: chỉ đổi được qua
 * {@link #start()}, {@link #submitForReview(boolean)}, {@link #approve()}, {@link #requestRevision(String)}.
 */
@Entity
@Table(name = "task")
@SQLRestriction("deleted_at IS NULL")
public class Task extends BaseEntity {

	@Column(nullable = false)
	private Long teamId;

	@Column(nullable = false)
	private String title;

	private String description;

	@Column(nullable = false)
	private Long assigneeId;

	@Column(nullable = false)
	private Long assignedBy;

	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TaskPriority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TaskStatus status;

	@Column(nullable = false)
	private boolean requireFile;

	/** Lý do của lần trả lại gần nhất; xoá khi task được duyệt. */
	private String revisionReason;

	protected Task() {
		// JPA
	}

	public static Task create(Long teamId, String title, String description, Long assigneeId, Long assignedBy,
			LocalDate dueDate, TaskPriority priority, boolean requireFile) {
		Task task = new Task();
		task.teamId = teamId;
		task.title = requireText(title, "Tiêu đề");
		task.description = blankToNull(description);
		task.assigneeId = assigneeId;
		task.assignedBy = assignedBy;
		task.dueDate = dueDate;
		task.priority = priority == null ? TaskPriority.MEDIUM : priority;
		task.requireFile = requireFile;
		task.status = TaskStatus.ASSIGNED;
		return task;
	}

	/** Sửa thông tin (không đổi trạng thái). Task đã DONE thì không sửa được. */
	public void update(String title, String description, Long assigneeId, LocalDate dueDate, TaskPriority priority,
			boolean requireFile) {
		if (status == TaskStatus.DONE) {
			throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "Task đã hoàn thành, không thể sửa");
		}
		this.title = requireText(title, "Tiêu đề");
		this.description = blankToNull(description);
		this.assigneeId = assigneeId;
		this.dueDate = dueDate;
		this.priority = priority == null ? TaskPriority.MEDIUM : priority;
		this.requireFile = requireFile;
	}

	/** ASSIGNED → IN_PROGRESS. */
	public void start() {
		require(TaskStatus.ASSIGNED, "bắt đầu");
		status = TaskStatus.IN_PROGRESS;
	}

	/**
	 * IN_PROGRESS → PENDING_REVIEW. Task yêu cầu file thì phải đã có ít nhất một file nộp.
	 *
	 * @param hasSubmission task đã có file nộp
	 */
	public void submitForReview(boolean hasSubmission) {
		require(TaskStatus.IN_PROGRESS, "nộp để duyệt");
		if (requireFile && !hasSubmission) {
			throw new BusinessException(ErrorCode.FILE_REQUIRED);
		}
		status = TaskStatus.PENDING_REVIEW;
	}

	/** PENDING_REVIEW → DONE. */
	public void approve() {
		require(TaskStatus.PENDING_REVIEW, "duyệt");
		status = TaskStatus.DONE;
		revisionReason = null;
	}

	/** PENDING_REVIEW → IN_PROGRESS, ghi lý do để người phụ trách làm lại. */
	public void requestRevision(String reason) {
		require(TaskStatus.PENDING_REVIEW, "trả lại");
		revisionReason = requireText(reason, "Lý do trả lại");
		status = TaskStatus.IN_PROGRESS;
	}

	public boolean isDone() {
		return status == TaskStatus.DONE;
	}

	public Long getTeamId() {
		return teamId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Long getAssigneeId() {
		return assigneeId;
	}

	public Long getAssignedBy() {
		return assignedBy;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public TaskPriority getPriority() {
		return priority;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public boolean isRequireFile() {
		return requireFile;
	}

	public String getRevisionReason() {
		return revisionReason;
	}

	private void require(TaskStatus expected, String action) {
		if (status != expected) {
			throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
					"Không thể " + action + " task đang ở trạng thái " + status);
		}
	}

	private static String requireText(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new ValidationException(label + " không được để trống");
		}
		return value.trim();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
