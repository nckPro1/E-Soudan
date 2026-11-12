package com.soudan.common.security;

import java.util.Optional;

/**
 * Cổng để AccessPolicy hỏi "người này giữ vai trò gì trong nhóm". Module team hiện thực;
 * đặt interface ở common để common không phụ thuộc ngược vào team.
 */
public interface TeamMembershipLookup {

	Optional<TeamRole> roleOf(Long teamId, Long userId);
}
