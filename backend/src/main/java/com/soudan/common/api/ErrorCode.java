package com.soudan.common.api;

import org.springframework.http.HttpStatus;

/** Mã lỗi tập trung. Mỗi mã gắn với một HTTP status (ARCHITECTURE.md 5.2). */
public enum ErrorCode {

	BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc phiên không hợp lệ"),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
	ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá"),
	PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Cần đổi mật khẩu trước khi tiếp tục"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền thực hiện"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
	CONFLICT(HttpStatus.CONFLICT, "Xung đột dữ liệu"),
	VERSION_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu vừa được người khác thay đổi, hãy tải lại"),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
	LAST_ADMIN_PROTECTED(HttpStatus.CONFLICT, "Không thể khoá hoặc hạ quyền Admin cuối cùng"),
	TEAM_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên nhóm đã được dùng cho một nhóm đang hoạt động"),
	TEAM_ARCHIVED(HttpStatus.CONFLICT, "Nhóm đã lưu trữ, không thể thay đổi"),
	LAST_LEADER_PROTECTED(HttpStatus.CONFLICT, "Nhóm phải còn ít nhất một Leader"),
	ALREADY_TEAM_MEMBER(HttpStatus.CONFLICT, "Người này đã có trong nhóm"),
	INVALID_TEAM_MEMBER(HttpStatus.UNPROCESSABLE_CONTENT, "Tài khoản này không được giữ vai trò đó trong nhóm"),
	INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Trạng thái hiện tại không cho phép thao tác này"),
	FILE_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Task yêu cầu nộp ít nhất một file"),
	ASSIGNEE_NOT_IN_TEAM(HttpStatus.UNPROCESSABLE_CONTENT, "Người phụ trách phải là thành viên của nhóm"),
	INVALID_FILE(HttpStatus.UNPROCESSABLE_CONTENT, "File không hợp lệ"),
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "File quá lớn"),
	SELF_REACTION(HttpStatus.FORBIDDEN, "Không thể thả biểu cảm vào bình luận của chính mình"),
	INVALID_EMOJI(HttpStatus.UNPROCESSABLE_CONTENT, "Biểu cảm không hợp lệ"),
	TOO_MANY_REACTIONS(HttpStatus.UNPROCESSABLE_CONTENT, "Bình luận này đã có quá nhiều loại biểu cảm"),
	ALREADY_COMMUNITY_MEMBER(HttpStatus.CONFLICT, "Người này đã có trong cộng đồng"),
	INVALID_COMMUNITY_MEMBER(HttpStatus.UNPROCESSABLE_CONTENT, "Chỉ thêm được tài khoản Leader/Member đang hoạt động"),
	REPORT_NOT_EDITABLE(HttpStatus.CONFLICT, "Báo cáo của ngày trước không sửa được nữa"),
	NOT_COMMUNITY_MEMBER(HttpStatus.FORBIDDEN, "Bạn không phải thành viên của cộng đồng này"),
	TOO_MANY_ATTACHMENTS(HttpStatus.UNPROCESSABLE_CONTENT, "Mỗi báo cáo đính kèm tối đa 10 file"),
	CHAT_ADMIN_EXCLUDED(HttpStatus.FORBIDDEN, "Admin không tham gia trò chuyện"),
	INVALID_CHAT_TARGET(HttpStatus.UNPROCESSABLE_CONTENT, "Không thể nhắn tin cho tài khoản này"),
	CHAT_REQUEST_DECLINED(HttpStatus.CONFLICT, "Đối phương đã từ chối lời mời nhắn tin"),
	CHAT_NOT_ACCEPTED(HttpStatus.CONFLICT, "Cần đối phương chấp nhận lời mời trước khi nhắn tin"),
	CHAT_READ_ONLY(HttpStatus.CONFLICT, "Cuộc trò chuyện này chỉ để xem, không gửi thêm được"),
	EMPTY_MESSAGE(HttpStatus.UNPROCESSABLE_CONTENT, "Hãy nhập nội dung hoặc đính kèm ít nhất một file"),
	TOO_MANY_ATTACHMENTS_CHAT(HttpStatus.UNPROCESSABLE_CONTENT, "Mỗi tin nhắn đính kèm tối đa 10 file"),
	CALENDAR_TOO_MANY_NOTES(HttpStatus.UNPROCESSABLE_CONTENT, "Mỗi ngày chỉ đánh dấu được một số ghi chú nhất định"),
	AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Trợ lý AI chưa được cấu hình"),
	AI_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã dùng trợ lý AI quá nhiều lần trong giờ qua, hãy thử lại sau"),
	AI_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "Không kết nối được tới mô hình AI, hãy thử lại sau"),
	AI_NO_DATA(HttpStatus.UNPROCESSABLE_CONTENT, "Không có dữ liệu để xử lý"),
	MEMBER_HAS_OPEN_TASKS(HttpStatus.CONFLICT, "Người này còn việc chưa xong trong nhóm, hãy chuyển giao hoặc hoàn thành trước khi gỡ"),
	INVALID_DUE_DATE(HttpStatus.UNPROCESSABLE_CONTENT, "Hạn chót không được nằm trong quá khứ"),
	ASSIGNEE_INACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "Người phụ trách đang bị khoá hoặc không còn hoạt động"),
	VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "Dữ liệu không hợp lệ"),
	CURRENT_PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_CONTENT, "Mật khẩu hiện tại không đúng"),
	INVALID_FILTER(HttpStatus.UNPROCESSABLE_CONTENT, "Trường lọc không được phép"),
	INVALID_SORT(HttpStatus.UNPROCESSABLE_CONTENT, "Trường sắp xếp không được phép"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
