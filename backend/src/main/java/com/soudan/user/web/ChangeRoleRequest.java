package com.soudan.user.web;

import com.soudan.common.security.SystemRole;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull SystemRole systemRole, @NotNull Long version) {
}
