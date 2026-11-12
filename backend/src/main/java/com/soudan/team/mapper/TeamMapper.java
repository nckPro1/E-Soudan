package com.soudan.team.mapper;

import org.mapstruct.Mapper;

import com.soudan.team.service.TeamDetail;
import com.soudan.team.service.TeamSummary;
import com.soudan.team.web.TeamDetailResponse;
import com.soudan.team.web.TeamResponse;

@Mapper(componentModel = "spring")
public interface TeamMapper {

	TeamResponse toResponse(TeamSummary summary);

	TeamDetailResponse toResponse(TeamDetail detail);
}
