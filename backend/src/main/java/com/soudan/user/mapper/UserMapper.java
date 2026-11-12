package com.soudan.user.mapper;

import org.mapstruct.Mapper;

import com.soudan.user.domain.User;
import com.soudan.user.web.UserResponse;

/** MapStruct sinh lớp hiện thực lúc biên dịch; không có mã ánh xạ viết tay. */
@Mapper(componentModel = "spring")
public interface UserMapper {

	UserResponse toResponse(User user);
}
