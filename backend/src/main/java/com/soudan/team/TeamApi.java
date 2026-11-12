package com.soudan.team;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Giao diện công khai của module team cho module khác (ví dụ task) gọi.
 * Module khác không được import entity hay repository của team (ARCHITECTURE.md 4.2).
 * Vai trò của một người trong nhóm hỏi qua {@code TeamMembershipLookup} (common.security).
 */
public interface TeamApi {

	/** Tra nhiều nhóm một lượt. Id không tồn tại thì không có trong kết quả. */
	List<TeamInfo> findTeams(Collection<Long> ids);

	default Optional<TeamInfo> findTeam(Long id) {
		return findTeams(List.of(id)).stream().findFirst();
	}
}
