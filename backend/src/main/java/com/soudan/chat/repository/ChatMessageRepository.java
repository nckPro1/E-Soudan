package com.soudan.chat.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.chat.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** Trang tin mới nhất của một cuộc trò chuyện (mới nhất trước). */
	@Query("select m from ChatMessage m where m.conversationId = :conversationId order by m.id desc")
	List<ChatMessage> latest(@Param("conversationId") Long conversationId, Pageable pageable);

	/** Trang tin cũ hơn một mốc id (mới nhất trước), để cuộn lên xem lịch sử. */
	@Query("select m from ChatMessage m where m.conversationId = :conversationId and m.id < :beforeId order by m.id desc")
	List<ChatMessage> olderThan(@Param("conversationId") Long conversationId, @Param("beforeId") Long beforeId,
			Pageable pageable);

	@Query("select m from ChatMessage m where m.id in :ids")
	List<ChatMessage> findAllByIds(@Param("ids") Collection<Long> ids);

	/** Số tin của người khác chưa đọc theo từng cuộc trò chuyện, chỉ tính các cuộc đã có mốc đọc. */
	@Query("""
			select m.conversationId as conversationId, count(m) as total
			from ChatMessage m, ChatRead r
			where r.key.conversationId = m.conversationId and r.key.userId = :userId
			  and m.id > r.lastReadMessageId and m.authorId <> :userId and m.deletedAt is null
			group by m.conversationId""")
	List<UnreadCount> unreadCounts(@Param("userId") Long userId);

	interface UnreadCount {
		Long getConversationId();

		long getTotal();
	}

	/** Tin chưa xoá của các cuộc trò chuyện từ mốc {@code since}, mới nhất trước (cho trợ lý AI). */
	@Query("select m from ChatMessage m where m.conversationId in :ids and m.createdAt >= :since and m.deletedAt is null order by m.id desc")
	List<ChatMessage> liveSince(@Param("ids") Collection<Long> ids, @Param("since") Instant since, Pageable pageable);
}
