package com.soudan.task.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ConflictException;
import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.file.FileStorage;
import com.soudan.common.file.UploadPolicy;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.task.domain.Task;
import com.soudan.task.domain.TaskStatus;
import com.soudan.task.domain.TaskSubmission;
import com.soudan.task.repository.TaskRepository;
import com.soudan.task.repository.TaskSubmissionRepository;
import com.soudan.task.service.TaskViews.SubmissionView;

/**
 * Nộp và tải file của task. Người phụ trách nộp khi task đang IN_PROGRESS (có thể nộp nhiều lần);
 * tải file chỉ người phụ trách và Leader của nhóm (Observer, Member khác và Admin không tải được).
 */
@Service
@Transactional
public class TaskSubmissionService {

	private final TaskRepository tasks;
	private final TaskSubmissionRepository submissions;
	private final FileStorage storage;
	private final UploadPolicy policy;
	private final AccessPolicy access;
	private final CurrentUser currentUser;
	private final Clock clock;

	public TaskSubmissionService(TaskRepository tasks, TaskSubmissionRepository submissions, FileStorage storage,
			UploadPolicy policy, AccessPolicy access, CurrentUser currentUser, Clock clock) {
		this.tasks = tasks;
		this.submissions = submissions;
		this.storage = storage;
		this.policy = policy;
		this.access = access;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	/** Một file đã mở để tải; người gọi đóng {@code content}. */
	public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
	}

	public SubmissionView submit(Long taskId, String originalName, long sizeBytes, InputStream content) {
		Task task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Không tìm thấy task " + taskId));
		Long me = currentUser.require().id();
		if (!task.getAssigneeId().equals(me)) {
			throw new ForbiddenException("Chỉ người được giao việc mới nộp được file");
		}
		if (task.getStatus() != TaskStatus.IN_PROGRESS) {
			throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "Chỉ nộp file khi task đang thực hiện");
		}

		String key;
		String safeName = policy.safeName(originalName);
		try (BufferedInputStream in = new BufferedInputStream(content)) {
			in.mark(UploadPolicy.HEAD_BYTES);
			byte[] head = in.readNBytes(UploadPolicy.HEAD_BYTES);
			in.reset();
			policy.validate(safeName, sizeBytes, head);
			key = storage.save(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		try {
			TaskSubmission saved = submissions.saveAndFlush(new TaskSubmission(taskId, safeName,
					policy.contentTypeOf(safeName), sizeBytes, key, me, clock.instant()));
			return new SubmissionView(saved.getId(), saved.getFileName(), saved.getContentType(),
					saved.getSizeBytes(), saved.getUploadedBy(), saved.getUploadedAt());
		} catch (RuntimeException e) {
			deleteQuietly(key);
			throw e;
		}
	}

	@Transactional(readOnly = true)
	public Download download(Long taskId, Long submissionId) {
		Task task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Không tìm thấy task " + taskId));
		boolean allowed = task.getAssigneeId().equals(currentUser.require().id())
				|| access.isTeamLeader(task.getTeamId());
		if (!allowed) {
			throw new ForbiddenException("Bạn không có quyền tải file này");
		}
		TaskSubmission file = submissions.findByIdAndTaskId(submissionId, taskId)
				.orElseThrow(() -> new NotFoundException("Không tìm thấy file " + submissionId));
		try {
			return new Download(file.getFileName(), file.getContentType(), file.getSizeBytes(),
					storage.load(file.getStorageKey()));
		} catch (IOException e) {
			throw new NotFoundException("File không còn trên máy chủ");
		}
	}

	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		} catch (IOException ignored) {
			// Dọn dẹp thất bại không che lỗi gốc.
		}
	}
}
