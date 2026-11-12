package com.soudan.task.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.soudan.task.service.TaskViews.CommentView;
import com.soudan.task.service.TaskViews.HistoryView;
import com.soudan.task.service.TaskViews.SubmissionView;
import com.soudan.task.service.TaskViews.TaskView;
import com.soudan.task.web.CommentResponse;
import com.soudan.task.web.HistoryResponse;
import com.soudan.task.web.TaskResponse;
import com.soudan.task.web.TaskResponse.SubmissionResponse;

@Mapper(componentModel = "spring")
public interface TaskMapper {

	TaskResponse toResponse(TaskView view);

	SubmissionResponse toResponse(SubmissionView view);

	HistoryResponse toResponse(HistoryView view);

	List<HistoryResponse> toHistory(List<HistoryView> views);

	CommentResponse toResponse(CommentView view);

	List<CommentResponse> toComments(List<CommentView> views);
}
