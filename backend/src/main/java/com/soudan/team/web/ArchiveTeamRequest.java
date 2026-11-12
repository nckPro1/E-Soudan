package com.soudan.team.web;

import jakarta.validation.constraints.NotNull;

public record ArchiveTeamRequest(@NotNull Long version) {
}
