package com.soudan.common.security;

/**
 * Cổng để module team hỏi "người này còn việc dở dang trong nhóm không" trước khi gỡ họ khỏi nhóm. Đặt interface ở common
 * để team không phụ thuộc ngược vào task (task đã phụ thuộc team); module có dữ liệu (task) hiện thực.
 */
public interface TeamMemberRemovalGuard {

	/** Số việc chưa xong trong nhóm này mà người đó đang phụ trách. 0 nghĩa là gỡ được. */
	long openWorkOf(Long teamId, Long userId);
}
