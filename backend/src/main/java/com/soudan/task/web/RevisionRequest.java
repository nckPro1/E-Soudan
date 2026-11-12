package com.soudan.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RevisionRequest(@NotNull Long version, @NotBlank @Size(max = 1000) String reason) {
}
