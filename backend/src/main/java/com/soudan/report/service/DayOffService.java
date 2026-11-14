package com.soudan.report.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.common.exception.ForbiddenException;
import com.soudan.common.exception.NotFoundException;
import com.soudan.common.exception.ValidationException;
import com.soudan.common.security.AccessPolicy;
import com.soudan.common.security.CurrentUser;
import com.soudan.report.domain.ReportDayOff;
import com.soudan.report.repository.ReportDayOffRepository;

/**
 * Báo nghỉ của chính mình: ngày nghỉ không bị tính là "chưa nộp báo cáo" và không nhận nhắc nộp. Chỉ vai trò Thành viên
 * (người nộp báo cáo) báo nghỉ; chỉ báo được từ hôm nay đến tối đa 120 ngày tới, và chỉ sửa/bỏ báo nghỉ của mình.
 */
@Service
@Transactional
public class DayOffService {

	public static final int MAX_AHEAD_DAYS = 120;
	public static final int MAX_RANGE_DAYS = 200;

	private final ReportDayOffRepository dayOffs;
	private final CurrentUser currentUser;
	private final AccessPolicy policy;
	private final Clock clock;
	private final ZoneId zone;

	public DayOffService(ReportDayOffRepository dayOffs, CurrentUser currentUser, AccessPolicy policy, Clock clock,
			ZoneId zone) {
		this.dayOffs = dayOffs;
		this.currentUser = currentUser;
		this.policy = policy;
		this.clock = clock;
		this.zone = zone;
	}

	@Transactional(readOnly = true)
	public List<ReportDayOff> list(LocalDate from, LocalDate to) {
		Long me = me();
		if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
			throw new ValidationException("Khoảng ngày không hợp lệ");
		}
		return dayOffs.findInRange(me, from, to);
	}

	/** Báo nghỉ một ngày; báo lại ngày đã nghỉ thì chỉ cập nhật lý do. */
	public ReportDayOff mark(LocalDate day, String reason) {
		Long me = me();
		LocalDate today = LocalDate.now(clock.withZone(zone));
		if (day == null || day.isBefore(today) || day.isAfter(today.plusDays(MAX_AHEAD_DAYS))) {
			throw new ValidationException("Chỉ báo nghỉ được từ hôm nay đến " + MAX_AHEAD_DAYS + " ngày tới");
		}
		ReportDayOff existing = dayOffs.findByUserIdAndDay(me, day).orElse(null);
		if (existing != null) {
			existing.changeReason(reason);
			return existing;
		}
		return dayOffs.save(new ReportDayOff(me, day, reason, Instant.now(clock)));
	}

	public void unmark(LocalDate day) {
		Long me = me();
		LocalDate today = LocalDate.now(clock.withZone(zone));
		if (day == null || day.isBefore(today)) {
			throw new ValidationException("Không bỏ báo nghỉ của ngày đã qua");
		}
		dayOffs.delete(dayOffs.findByUserIdAndDay(me, day).orElseThrow(() -> new NotFoundException("Chưa báo nghỉ ngày này")));
	}

	private Long me() {
		if (policy.isAdmin() || policy.isManager()) {
			throw new ForbiddenException("Chỉ thành viên nộp báo cáo mới báo nghỉ");
		}
		return currentUser.require().id();
	}
}
