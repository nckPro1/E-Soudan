package com.soudan.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
		@NotBlank @Size(max = 100) String fullName,
		@Size(max = 100) String position,
		@NotNull Long version) {
}
