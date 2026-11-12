package com.soudan.task.web;

import java.time.Instant;

import com.soudan.task.web.TaskResponse.PersonResponse;

public record CommentResponse(Long id, PersonResponse author, String body, Instant createdAt) {
}
