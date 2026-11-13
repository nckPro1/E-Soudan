package com.soudan.notification.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@Query("select n from Notification n where n.recipientId = :userId order by n.createdAt desc, n.id desc")
	Page<Notification> findAllOf(@Param("userId") Long userId, Pageable pageable);

	@Query("select n from Notification n where n.recipientId = :userId and n.readAt is null order by n.createdAt desc, n.id desc")
	Page<Notification> findUnreadOf(@Param("userId") Long userId, Pageable pageable);

	long countByRecipientIdAndReadAtIsNull(Long recipientId);

	Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

	@Modifying(clearAutomatically = true)
	@Query("update Notification n set n.readAt = :at where n.recipientId = :userId and n.readAt is null")
	int markAllRead(@Param("userId") Long userId, @Param("at") Instant at);
}
