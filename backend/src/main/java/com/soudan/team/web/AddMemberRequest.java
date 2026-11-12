package com.soudan.team.web;

import com.soudan.common.security.TeamRole;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotNull Long userId, @NotNull TeamRole teamRole) {
}
