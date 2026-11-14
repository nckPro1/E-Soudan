package com.soudan.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.Test;

class OperatingHoursPropertiesTest {

	private static OperatingHoursProperties of(String start, String end, String zone) {
		return new OperatingHoursProperties(true, start, end, List.of(DayOfWeek.MONDAY), zone);
	}

	@Test
	void should_rejectEndNotAfterStart() {
		assertThatThrownBy(() -> of("18:00", "08:30", "Asia/Ho_Chi_Minh")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> of("08:30", "08:30", "Asia/Ho_Chi_Minh")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void should_rejectBadFormatOrZone() {
		assertThatThrownBy(() -> of("8h30", "18:00", "Asia/Ho_Chi_Minh")).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> of("08:30", "18:00", "Mars/Olympus")).isInstanceOf(RuntimeException.class);
	}
}
