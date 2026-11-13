package com.soudan.task;

import java.time.Instant;
import java.util.List;

import com.soudan.common.ai.KnowledgeDoc;

/** Giao diện công khai của module task cho module khác (trợ lý AI). Chỉ trả về dữ liệu người đang đăng nhập được xem. */
public interface TaskApi {

	/** Việc giao cho người đang đăng nhập còn chưa xong (chưa duyệt), hạn gần nhất trước. */
	List<KnowledgeDoc> myOpenTasks(int limit);

	/** Việc giao cho người đang đăng nhập có thay đổi từ mốc {@code since} (để soạn nháp báo cáo). */
	List<KnowledgeDoc> myTasksChangedSince(Instant since, int limit);
}
