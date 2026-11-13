package com.soudan.calendar.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.soudan.calendar.CalendarApi;
import com.soudan.calendar.domain.CalendarNote;
import com.soudan.common.ai.KnowledgeDoc;
import com.soudan.common.ai.KnowledgeSource;
import com.soudan.common.security.CurrentUser;

/** Ghi chú lịch cho trợ lý AI: chỉ ghi chú của chính người hỏi. */
@Component
@Transactional(readOnly = true)
class CalendarKnowledge implements CalendarApi, KnowledgeSource {

	/** Truy xuất chung cũng nhìn trước một khoảng để trả lời "tuần sau tôi có gì". */
	private static final int LOOKAHEAD_DAYS = 30;

	private final CalendarService service;
	private final Clock clock;
	private final ZoneId zone;

	CalendarKnowledge(CalendarService service, CurrentUser currentUser, Clock clock, ZoneId zone) {
		this.service = service;
		this.clock = clock;
		this.zone = zone;
	}

	@Override
	public String kind() {
		return "NOTE";
	}

	@Override
	public List<KnowledgeDoc> myNotes(LocalDate from, LocalDate to) {
		return service.list(from, to).stream().map(CalendarKnowledge::doc).toList();
	}

	@Override
	public List<KnowledgeDoc> visibleSince(Instant since, int limit) {
		// CalendarService giới hạn khoảng xem, nên không nhìn lại quá 60 ngày
		LocalDate floor = LocalDate.now(clock.withZone(zone)).minusDays(60);
		LocalDate from = LocalDate.ofInstant(since, zone);
		from = from.isBefore(floor) ? floor : from;
		List<KnowledgeDoc> all = myNotes(from, LocalDate.now(clock.withZone(zone)).plusDays(LOOKAHEAD_DAYS));
		return all.size() > limit ? all.subList(all.size() - limit, all.size()) : all;
	}

	private static KnowledgeDoc doc(CalendarNote n) {
		String text = "Ngày " + n.getNoteDate() + ". " + (n.getContent() == null ? "" : n.getContent());
		return new KnowledgeDoc("NOTE", n.getId(), "Ghi chú lịch: " + n.getTitle(), text,
				n.getNoteDate().atStartOfDay().toInstant(ZoneOffset.UTC), null, "/calendar");
	}
}
