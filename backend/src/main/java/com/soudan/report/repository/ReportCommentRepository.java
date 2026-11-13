package com.soudan.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.report.domain.ReportComment;

public interface ReportCommentRepository extends JpaRepository<ReportComment, Long> {

	Optional<ReportComment> findByIdAndPostId(Long id, Long postId);

	List<ReportComment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId);

	@Query("select c.postId as postId, count(c) as total from ReportComment c where c.postId in :ids and c.deletedAt is null group by c.postId")
	List<CommentCount> countLiveByPosts(@Param("ids") Collection<Long> ids);

	interface CommentCount {
		Long getPostId();

		long getTotal();
	}
}
