package com.soudan.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactionRequest(@NotBlank @Size(max = 32) String emoji) {
}
