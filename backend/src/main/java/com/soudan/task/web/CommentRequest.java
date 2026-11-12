package com.soudan.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Viết hoặc sửa bình luận. {@code replyToId} chỉ có nghĩa khi viết mới (bình luận đang được trả lời). */
public record CommentRequest(@NotBlank @Size(max = 2000) String body, Long replyToId) {
}
