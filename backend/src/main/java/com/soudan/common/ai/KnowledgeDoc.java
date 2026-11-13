package com.soudan.common.ai;

import java.time.Instant;

/**
 * Một mẩu dữ liệu mà module trợ lý AI được phép đọc để trả lời (báo cáo, công việc, một ngày chat nhóm, ghi chú lịch).
 * Module sở hữu dữ liệu tạo ra và tự lọc theo quyền của người đang hỏi; module AI không bao giờ tự đọc bảng của module khác.
 *
 * @param kind   REPORT, TASK, TEAM_CHAT, NOTE, CHAT (một cuộc trò chuyện người dùng chủ động chọn tóm tắt)
 * @param id     id của đối tượng gốc (báo cáo, task...)
 * @param title  tiêu đề ngắn để hiển thị ở phần nguồn trích dẫn
 * @param text   nội dung văn bản thuần
 * @param at     thời điểm của dữ liệu (dùng để ưu tiên cái mới)
 * @param author tên người viết (nếu có)
 * @param path   đường dẫn trong ứng dụng để mở đúng đối tượng gốc, ví dụ /tasks/5
 */
public record KnowledgeDoc(String kind, Long id, String title, String text, Instant at, String author, String path) {
}
