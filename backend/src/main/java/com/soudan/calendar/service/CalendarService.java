package com.soudan.calendar.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.calendar.domain.CalendarNote;
import com.soudan.calendar.repository.CalendarNoteRepository;
import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.BusinessException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.security.CurrentUser;

/**
 * Lịch cá nhân: mỗi người (mọi vai trò) đánh dấu ghi chú theo ngày. Ghi chú của người khác coi như không tồn tại (404),
 * nên không ai đọc hay sửa được lịch của nhau.
 */
@Service
@Transactional
public class CalendarService {

	/** Khoảng xem tối đa một lần (đủ cho lưới 6 tuần của một tháng, có dư). */
	public static final int MAX_RANGE_DAYS = 100;
	public static final int MAX_NOTES_PER_DAY = 20;

	private final CalendarNoteRepository notes;
	private final CurrentUser currentUser;
	private final Clock clock;

	public CalendarService(CalendarNoteRepository notes, CurrentUser currentUser, Clock clock) {
		this.notes = notes;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<CalendarNote> list(LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from)) {
			throw new ValidationException("Khoảng ngày không hợp lệ");
		}
		if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
			throw new ValidationException("Chỉ xem được tối đa " + MAX_RANGE_DAYS + " ngày một lần");
		}
		return notes.findInRange(currentUser.require().id(), from, to);
	}

	public CalendarNote create(LocalDate date, String title, String content, String color) {
		Long me = currentUser.require().id();
		requireRoom(me, date);
		return notes.save(new CalendarNote(me, date, title, content, color, Instant.now(clock)));
	}

	public CalendarNote update(Long id, LocalDate date, String title, String content, String color) {
		CalendarNote note = own(id);
		if (date != null && !date.equals(note.getNoteDate())) {
			requireRoom(note.getUserId(), date);
		}
		note.change(date, title, content, color, Instant.now(clock));
		return note;
	}

	public void delete(Long id) {
		notes.delete(own(id));
	}

	private void requireRoom(Long userId, LocalDate date) {
		if (date != null && notes.countByUserIdAndNoteDate(userId, date) >= MAX_NOTES_PER_DAY) {
			throw new BusinessException(ErrorCode.CALENDAR_TOO_MANY_NOTES,
					"Mỗi ngày tối đa " + MAX_NOTES_PER_DAY + " ghi chú");
		}
	}

	private CalendarNote own(Long id) {
		return notes.findByIdAndUserId(id, currentUser.require().id())
				.orElseThrow(() -> new NotFoundException("Không tìm thấy ghi chú"));
	}
}
