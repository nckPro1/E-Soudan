package com.soudan.team.web;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 500) String description,
		@NotEmpty List<@NotNull Long> leaderIds,
		List<@NotNull Long> memberIds,
		List<@NotNull Long> observerIds) {
}
