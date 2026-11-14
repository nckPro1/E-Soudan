package com.soudan.report.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.soudan.report.service.ReportViews.AttachmentView;
import com.soudan.report.service.ReportViews.CommentView;
import com.soudan.report.service.ReportViews.CommunityDetail;
import com.soudan.report.service.ReportViews.CommunitySummary;
import com.soudan.report.service.ReportViews.PendingView;
import com.soudan.report.service.ReportViews.PersonView;
import com.soudan.report.service.ReportViews.PostView;
import com.soudan.report.service.ReportViews.ReactionView;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Request và response của module report (record). */
public final class ReportDtos {

	private ReportDtos() {
	}

	public record CommunityRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {
	}

	public record AddMembersRequest(@NotEmpty List<Long> userIds) {
	}

	public record CommentRequest(@NotBlank @Size(max = 2000) String body, Long replyToId) {
	}

	public record ReactionRequest(@NotBlank @Size(max = 32) String emoji) {
	}

	public record PersonResponse(Long id, String fullName, String position) {
		static PersonResponse of(PersonView v) {
			return v == null ? null : new PersonResponse(v.id(), v.fullName(), v.position());
		}
	}

	public record ReactionResponse(String emoji, int count, boolean reactedByMe, List<String> users) {
		static ReactionResponse of(ReactionView v) {
			return new ReactionResponse(v.emoji(), v.count(), v.reactedByMe(), v.users());
		}
	}

	public record CommunitySummaryResponse(Long id, String name, String description, PersonResponse owner,
			int memberCount, boolean owned) {
		static CommunitySummaryResponse of(CommunitySummary v) {
			return new CommunitySummaryResponse(v.id(), v.name(), v.description(), PersonResponse.of(v.owner()),
					v.memberCount(), v.owned());
		}
	}

	public record CommunityDetailResponse(CommunitySummaryResponse community, List<PersonResponse> members,
			Long version) {
		static CommunityDetailResponse of(CommunityDetail v) {
			return new CommunityDetailResponse(CommunitySummaryResponse.of(v.community()),
					v.members().stream().map(PersonResponse::of).toList(), v.version());
		}
	}

	public record PostResponse(Long id, Long communityId, PersonResponse author, LocalDate reportDate,
			String title, String body, List<AttachmentResponse> attachments, boolean editable, boolean edited,
			Instant createdAt, Long version, int commentCount, List<ReactionResponse> reactions) {
		static PostResponse of(PostView v) {
			return new PostResponse(v.id(), v.communityId(), PersonResponse.of(v.author()), v.reportDate(),
					v.title(), v.body(), v.attachments().stream().map(AttachmentResponse::of).toList(), v.editable(), v.edited(), v.createdAt(), v.version(),
					v.commentCount(), v.reactions().stream().map(ReactionResponse::of).toList());
		}
	}

	public record AttachmentResponse(Long id, String fileName, String contentType, long sizeBytes, boolean image) {
		static AttachmentResponse of(AttachmentView v) {
			return new AttachmentResponse(v.id(), v.fileName(), v.contentType(), v.sizeBytes(), v.image());
		}
	}

	public record CommentResponse(Long id, Long parentId, PersonResponse author, String body, boolean deleted,
			Instant createdAt, Instant editedAt, List<ReactionResponse> reactions) {
		static CommentResponse of(CommentView v) {
			return new CommentResponse(v.id(), v.parentId(), PersonResponse.of(v.author()), v.body(), v.deleted(),
					v.createdAt(), v.editedAt(), v.reactions().stream().map(ReactionResponse::of).toList());
		}
	}

	public record PendingResponse(LocalDate date, boolean workingDay, List<PersonResponse> missing,
			List<PersonResponse> onLeave) {
		static PendingResponse of(PendingView v) {
			return new PendingResponse(v.date(), v.workingDay(), v.missing().stream().map(PersonResponse::of).toList(),
					v.onLeave().stream().map(PersonResponse::of).toList());
		}
	}
}
