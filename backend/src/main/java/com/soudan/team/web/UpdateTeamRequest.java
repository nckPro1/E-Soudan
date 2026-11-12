package com.soudan.team.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTeamRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 500) String description,
		@NotNull Long version) {
}
