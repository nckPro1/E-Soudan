package com.soudan.user.web;

import com.soudan.common.security.SystemRole;

/** Thông tin tối thiểu để chọn người; không lộ email hay trạng thái. */
public record UserOptionResponse(Long id, String fullName, String position, SystemRole systemRole) {
}
