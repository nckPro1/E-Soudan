package com.soudan.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;

/** Bảng chuyển trạng thái của Task: hợp lệ thì thành công, không hợp lệ thì lỗi và trạng thái không đổi. */
class TaskTest {

	private static Task newTask(boolean requireFile) {
		return Task.create(1L, "Viết báo cáo", null, 2L, 3L, LocalDate.of(2026, 12, 1), TaskPriority.HIGH, requireFile);
	}

	private static Task inStatus(TaskStatus target, boolean requireFile) {
		Task task = newTask(requireFile);
		if (target == TaskStatus.ASSIGNED) {
			return task;
		}
		task.start();
		if (target == TaskStatus.IN_PROGRESS) {
			return task;
		}
		task.submitForReview(true);
		if (target == TaskStatus.PENDING_REVIEW) {
			return task;
		}
		task.approve();
		return task;
	}

	private static ErrorCode codeOf(Runnable action) {
		try {
			action.run();
		} catch (BusinessException e) {
			return e.getCode();
		}
		return null;
	}

	@Test
	void should_startAsAssigned_when_created() {
		Task task = newTask(false);

		assertThat(task.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
		assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
	}

	@Test
	void should_walkTheHappyPath() {
		Task task = newTask(false);

		task.start();
		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		task.submitForReview(false);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING_REVIEW);
		task.approve();
		assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void should_returnToInProgressWithReason_when_revisionRequested() {
		Task task = inStatus(TaskStatus.PENDING_REVIEW, false);

		task.requestRevision("  Thiếu số liệu  ");

		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(task.getRevisionReason()).isEqualTo("Thiếu số liệu");
	}

	@Test
	void should_clearReason_when_approvedAfterRevision() {
		Task task = inStatus(TaskStatus.PENDING_REVIEW, false);
		task.requestRevision("Làm lại");
		task.submitForReview(false);

		task.approve();

		assertThat(task.getRevisionReason()).isNull();
	}

	@Test
	void should_rejectRevision_when_reasonBlank() {
		Task task = inStatus(TaskStatus.PENDING_REVIEW, false);

		assertThat(codeOf(() -> task.requestRevision("  "))).isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING_REVIEW);
	}

	@Test
	void should_refuseSubmit_when_fileRequiredButMissing() {
		Task task = inStatus(TaskStatus.IN_PROGRESS, true);

		assertThat(codeOf(() -> task.submitForReview(false))).isEqualTo(ErrorCode.FILE_REQUIRED);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		task.submitForReview(true);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING_REVIEW);
	}

	@ParameterizedTest
	@EnumSource(value = TaskStatus.class, names = { "IN_PROGRESS", "PENDING_REVIEW", "DONE" })
	void should_rejectStart_unlessAssigned(TaskStatus from) {
		assertRejected(from, Task::start);
	}

	@ParameterizedTest
	@EnumSource(value = TaskStatus.class, names = { "ASSIGNED", "PENDING_REVIEW", "DONE" })
	void should_rejectSubmit_unlessInProgress(TaskStatus from) {
		assertRejected(from, t -> t.submitForReview(true));
	}

	@ParameterizedTest
	@EnumSource(value = TaskStatus.class, names = { "ASSIGNED", "IN_PROGRESS", "DONE" })
	void should_rejectApprove_unlessPendingReview(TaskStatus from) {
		assertRejected(from, Task::approve);
	}

	@ParameterizedTest
	@EnumSource(value = TaskStatus.class, names = { "ASSIGNED", "IN_PROGRESS", "DONE" })
	void should_rejectRevision_unlessPendingReview(TaskStatus from) {
		assertRejected(from, t -> t.requestRevision("lý do"));
	}

	private static void assertRejected(TaskStatus from, Consumer<Task> action) {
		Task task = inStatus(from, false);

		assertThat(codeOf(() -> action.accept(task))).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
		assertThat(task.getStatus()).isEqualTo(from);
	}

	@Test
	void should_refuseSecondApprove_when_alreadyDone() {
		Task task = inStatus(TaskStatus.DONE, false);

		assertThat(codeOf(task::approve)).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void should_refuseUpdate_when_done() {
		Task task = inStatus(TaskStatus.DONE, false);

		assertThat(codeOf(() -> task.update("X", null, 2L, null, TaskPriority.LOW, false)))
				.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void should_updateFieldsButNotStatus_when_assigneeUnchanged() {
		Task task = inStatus(TaskStatus.IN_PROGRESS, false);

		boolean reassigned = task.update("Tiêu đề mới", " mô tả ", task.getAssigneeId(), null, null, true);

		assertThat(reassigned).isFalse();
		assertThat(task.getTitle()).isEqualTo("Tiêu đề mới");
		assertThat(task.getDescription()).isEqualTo("mô tả");
		assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
		assertThat(task.isRequireFile()).isTrue();
		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
	}

	@Test
	void should_restartFromAssignedAndClearRevisionReason_when_reassigned() {
		Task task = inStatus(TaskStatus.PENDING_REVIEW, false);
		task.requestRevision("Thiếu số liệu");

		boolean reassigned = task.update("T", null, 5L, null, null, false);

		assertThat(reassigned).isTrue();
		assertThat(task.getAssigneeId()).isEqualTo(5L);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
		assertThat(task.getRevisionReason()).isNull();
	}

	@Test
	void should_rejectBlankTitle() {
		assertThat(codeOf(() -> Task.create(1L, "  ", null, 2L, 3L, null, null, false)))
				.isEqualTo(ErrorCode.VALIDATION_ERROR);
	}
}
