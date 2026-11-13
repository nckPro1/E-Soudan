package com.soudan.calendar;

import java.time.LocalDate;
import java.util.List;

import com.soudan.common.ai.KnowledgeDoc;

/** Giao diện công khai của module calendar cho module khác (trợ lý AI). Chỉ trả về ghi chú của người đang đăng nhập. */
public interface CalendarApi {

	/** Ghi chú lịch của mình trong khoảng [from, to], theo ngày. */
	List<KnowledgeDoc> myNotes(LocalDate from, LocalDate to);
}
