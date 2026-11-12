package com.soudan.task.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.task.domain.CommentReaction;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

	List<CommentReaction> findByCommentIdInOrderByIdAsc(Collection<Long> commentIds);

	Optional<CommentReaction> findByCommentIdAndUserIdAndEmoji(Long commentId, Long userId, String emoji);

	@Query("select count(distinct r.emoji) from CommentReaction r where r.commentId = :commentId")
	long countDistinctEmoji(@Param("commentId") Long commentId);

	boolean existsByCommentIdAndEmoji(Long commentId, String emoji);
}
