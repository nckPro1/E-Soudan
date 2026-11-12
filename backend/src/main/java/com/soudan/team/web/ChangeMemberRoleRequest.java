package com.soudan.team.web;

import com.soudan.common.security.TeamRole;

import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(@NotNull TeamRole teamRole) {
}
