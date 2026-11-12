package com.soudan.user;

/** Phát ra khi một tài khoản bị khoá. Module auth lắng nghe để thu hồi các phiên đang có. */
public record UserLockedEvent(Long userId) {
}
