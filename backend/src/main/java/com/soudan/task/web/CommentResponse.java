package com.soudan.task.web;

import java.time.Instant;
import java.util.List;

import com.soudan.task.web.TaskResponse.PersonResponse;

/** Bình luận. {@code deleted} true: bình luận gốc đã xoá nhưng còn trả lời, nên {@code author} và {@code body} là null. */
public record CommentResponse(Long id, Long parentId, PersonResponse author, String body, boolean deleted,
		Instant createdAt, Instant editedAt, List<ReactionResponse> reactions) {

	public record ReactionResponse(String emoji, int count, boolean reactedByMe, List<String> users) {
	}
}
