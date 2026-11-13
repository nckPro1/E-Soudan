package com.soudan.ai.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soudan.ai.domain.AiUsage;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

	long countByUserIdAndCreatedAtAfter(Long userId, Instant after);
}
