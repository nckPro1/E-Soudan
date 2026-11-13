package com.soudan.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soudan.report.domain.ReportAttachment;

public interface ReportAttachmentRepository extends JpaRepository<ReportAttachment, Long> {

	List<ReportAttachment> findByPostIdOrderByIdAsc(Long postId);

	List<ReportAttachment> findByPostIdInOrderByIdAsc(Collection<Long> postIds);

	Optional<ReportAttachment> findByIdAndPostId(Long id, Long postId);
}
