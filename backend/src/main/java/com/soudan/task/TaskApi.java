package com.soudan.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.soudan.common.ai.KnowledgeDoc;

/** Giao diện công khai của module task cho module khác (trợ lý AI). Chỉ trả về dữ liệu người đang đăng nhập được xem. */
public interface TaskApi {

	/** Việc giao cho người đang đăng nhập còn chưa xong (chưa duyệt), hạn gần nhất trước. */
	List<KnowledgeDoc> myOpenTasks(int limit);

	/** Việc chưa xong có hạn trong khoảng [from, to], của mọi người (dùng cho việc nhắc hạn, không phụ thuộc người đăng nhập). */
	List<DueTask> openTasksDueBetween(LocalDate from, LocalDate to);

	record DueTask(Long taskId, Long teamId, String title, Long assigneeId, LocalDate dueDate) {
	}

	/** Việc giao cho người đang đăng nhập có thay đổi từ mốc {@code since} (để soạn nháp báo cáo). */
	List<KnowledgeDoc> myTasksChangedSince(Instant since, int limit);
}
