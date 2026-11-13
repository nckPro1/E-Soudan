package com.soudan.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.chat.domain.ChatReaction;

public interface ChatReactionRepository extends JpaRepository<ChatReaction, Long> {

	List<ChatReaction> findByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

	Optional<ChatReaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

	boolean existsByMessageIdAndEmoji(Long messageId, String emoji);

	@Query("select count(distinct r.emoji) from ChatReaction r where r.messageId = :messageId")
	long countDistinctEmoji(@Param("messageId") Long messageId);
}
