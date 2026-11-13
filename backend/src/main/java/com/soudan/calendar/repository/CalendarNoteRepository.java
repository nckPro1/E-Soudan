package com.soudan.calendar.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.calendar.domain.CalendarNote;

public interface CalendarNoteRepository extends JpaRepository<CalendarNote, Long> {

	@Query("select n from CalendarNote n where n.userId = :userId and n.noteDate between :from and :to "
			+ "order by n.noteDate asc, n.id asc")
	List<CalendarNote> findInRange(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	Optional<CalendarNote> findByIdAndUserId(Long id, Long userId);

	long countByUserIdAndNoteDate(Long userId, LocalDate noteDate);
}
