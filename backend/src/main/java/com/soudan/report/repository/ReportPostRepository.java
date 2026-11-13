package com.soudan.report.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.report.domain.ReportPost;

public interface ReportPostRepository extends JpaRepository<ReportPost, Long> {

	@Query("select p from ReportPost p where p.id = :id and p.deletedAt is null")
	Optional<ReportPost> findLive(@Param("id") Long id);

	@Query("select p from ReportPost p where p.communityId = :communityId and p.authorId = :authorId and p.reportDate = :date and p.deletedAt is null")
	Optional<ReportPost> findOfDay(@Param("communityId") Long communityId, @Param("authorId") Long authorId,
			@Param("date") LocalDate date);

	/** Báo cáo của một thành viên trong cộng đồng, ngày mới nhất trước. */
	@Query("""
			select p from ReportPost p
			where p.communityId = :communityId and p.authorId = :authorId and p.deletedAt is null
			order by p.reportDate desc, p.id desc""")
	Page<ReportPost> feedOf(@Param("communityId") Long communityId, @Param("authorId") Long authorId,
			Pageable pageable);

	@Query("select p.authorId from ReportPost p where p.communityId = :communityId and p.reportDate = :date and p.deletedAt is null")
	List<Long> authorsOfDay(@Param("communityId") Long communityId, @Param("date") LocalDate date);

	/** Báo cáo của các cộng đồng trong khoảng ngày [from, to], ngày mới nhất trước (cho trợ lý AI). */
	@Query("""
			select p from ReportPost p
			where p.communityId in :communityIds and p.reportDate >= :from and p.reportDate <= :to and p.deletedAt is null
			order by p.reportDate desc, p.id desc""")
	List<ReportPost> inCommunitiesBetween(@Param("communityIds") Collection<Long> communityIds,
			@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);
}
