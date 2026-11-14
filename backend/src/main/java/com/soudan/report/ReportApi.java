package com.soudan.report;

import java.time.LocalDate;
import java.util.List;

import com.soudan.common.ai.KnowledgeDoc;

/**
 * Giao diện công khai của module report cho module khác (trợ lý AI). Chỉ trả về dữ liệu người đang đăng nhập được xem:
 * cùng quy tắc với feed thường (chủ cộng đồng hoặc thành viên).
 */
public interface ReportApi {

	/** Tên và báo cáo của một cộng đồng trong khoảng ngày [from, to], ngày mới nhất trước. Không phải thành viên thì 403. */
	CommunityDigest communityPosts(Long communityId, LocalDate from, LocalDate to, int limit);

	record CommunityDigest(String communityName, List<KnowledgeDoc> posts) {
	}

	/**
	 * Những người chưa nộp báo cáo của ngày {@code day}, theo từng cộng đồng, dành cho việc nhắc nộp (không phụ thuộc
	 * người đang đăng nhập). Ngày không phải ngày làm việc thì rỗng; bỏ người đã bị khoá và người đã báo nghỉ.
	 */
	List<MissingReports> missingOn(LocalDate day);

	record MissingReports(Long communityId, String communityName, List<Long> userIds) {
	}
}
