package com.soudan.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.report.domain.ReportReaction;

public interface ReportReactionRepository extends JpaRepository<ReportReaction, Long> {

	List<ReportReaction> findByPostIdInOrderByIdAsc(Collection<Long> postIds);

	List<ReportReaction> findByCommentIdInOrderByIdAsc(Collection<Long> commentIds);

	Optional<ReportReaction> findByPostIdAndUserIdAndEmoji(Long postId, Long userId, String emoji);

	Optional<ReportReaction> findByCommentIdAndUserIdAndEmoji(Long commentId, Long userId, String emoji);

	boolean existsByPostIdAndEmoji(Long postId, String emoji);

	boolean existsByCommentIdAndEmoji(Long commentId, String emoji);

	@Query("select count(distinct r.emoji) from ReportReaction r where r.postId = :postId")
	long countDistinctOnPost(@Param("postId") Long postId);

	@Query("select count(distinct r.emoji) from ReportReaction r where r.commentId = :commentId")
	long countDistinctOnComment(@Param("commentId") Long commentId);
}
