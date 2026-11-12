package com.soudan.team.service;

import java.util.List;

/** Dữ liệu tạo nhóm. Leader và Member là tài khoản USER; Observer là tài khoản MANAGER. */
public record CreateTeamCommand(String name, String description, List<Long> leaderIds, List<Long> memberIds,
		List<Long> observerIds) {
}
