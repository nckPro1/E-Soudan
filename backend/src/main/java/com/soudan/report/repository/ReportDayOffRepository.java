package com.soudan.report.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.report.domain.ReportDayOff;

public interface ReportDayOffRepository extends JpaRepository<ReportDayOff, Long> {

	Optional<ReportDayOff> findByUserIdAndDay(Long userId, LocalDate day);

	@Query("select d from ReportDayOff d where d.userId = :userId and d.day between :from and :to order by d.day")
	List<ReportDayOff> findInRange(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Những người nghỉ trong ngày này. */
	@Query("select d.userId from ReportDayOff d where d.day = :day")
	List<Long> userIdsOn(@Param("day") LocalDate day);
}
