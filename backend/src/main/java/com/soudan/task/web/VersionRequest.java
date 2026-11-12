package com.soudan.task.web;

import jakarta.validation.constraints.NotNull;

/** Body của các hành động đổi trạng thái: gửi lại version đang xem để phát hiện xung đột. */
public record VersionRequest(@NotNull Long version) {
}
