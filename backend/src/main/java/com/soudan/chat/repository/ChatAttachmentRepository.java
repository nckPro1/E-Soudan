package com.soudan.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soudan.chat.domain.ChatAttachment;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

	List<ChatAttachment> findByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

	Optional<ChatAttachment> findByIdAndMessageId(Long id, Long messageId);
}
