package com.soudan.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.chat.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

	@Query("select c from Conversation c where c.type = com.soudan.chat.domain.ConversationType.DIRECT and c.userLowId = :low and c.userHighId = :high")
	Optional<Conversation> findDirectBetween(@Param("low") Long low, @Param("high") Long high);

	/** Các cuộc nhắn riêng của một người (đang chờ hoặc đã chấp nhận; lời mời bị từ chối không hiện). */
	@Query("""
			select c from Conversation c
			where c.type = com.soudan.chat.domain.ConversationType.DIRECT
			  and (c.userLowId = :userId or c.userHighId = :userId)
			  and c.status <> com.soudan.chat.domain.DirectStatus.DECLINED
			order by coalesce(c.lastMessageAt, c.createdAt) desc, c.id desc""")
	List<Conversation> findDirectsOf(@Param("userId") Long userId);

	Optional<Conversation> findByTeamId(Long teamId);

	@Query("select c from Conversation c where c.teamId in :teamIds")
	List<Conversation> findByTeamIds(@Param("teamIds") java.util.Collection<Long> teamIds);
}
