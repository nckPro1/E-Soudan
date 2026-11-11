# Stage 4 — Báo cáo hằng ngày và nhận xét (từng bước)

> Mục tiêu: vòng báo cáo Member → Leader → Manager. Xem `IMPLEMENTATION_PLAN.md` Stage 4 và `BUSINESS_OVERVIEW.md` mục 8.
> Điều kiện vào stage: Stage 3 xong (`stage-3-done`).

## Tổng quan thứ tự

`4.1 test case → 4.2 migration → 4.3 domain → 4.4 ReportService → 4.5 AccessPolicy → 4.6 comment → 4.7 controller → 4.8 test → 4.9 frontend → 4.10 cổng`

---

### 4.1 Soạn test case — [AI soạn, BẠN duyệt]
- Phải có: Member nộp báo cáo; nộp lần hai cùng ngày cùng nhóm → xử lý đúng quy ước; Member xem báo cáo người khác → 403; Leader chỉ xem báo cáo nhóm mình lead; Leader nộp báo cáo cho Manager; **Manager xem được báo cáo Leader, không xem được báo cáo Member**; OBSERVER **không** xem báo cáo Member; nhận xét bởi người không có quyền → 403; báo cáo của nhóm đã lưu trữ.
- **Xong khi:** bạn duyệt.

### 4.2 Migration — [BẠN]
- `daily_report`: `id`, `team_id`, `author_id`, `report_date`, `done_text`, `plan_text`, `issues_text`, audit, `version`; ràng buộc **unique (team_id, author_id, report_date)**; chỉ mục `(team_id, report_date)`.
- Liên kết "task liên quan": bảng trung gian `daily_report_task (report_id, task_id)` — đây là bảng thứ 14 nếu bạn chọn cách này (cập nhật `ARCHITECTURE.md`). Phương án gọn hơn: để dành, chỉ ghi tên task trong văn bản.
- `report_comment`: `id`, `report_id`, `author_id`, `content`, audit.
- File: `V5__create_report_tables.sql`.
- **Xong khi:** migration chạy, ràng buộc unique hoạt động.

### 4.3 Domain — [BẠN]
- `DailyReport`, `ReportComment`. Quy tắc trong domain: báo cáo chỉ sửa được trong ngày `report_date`, và chỉ bởi tác giả [Mặc định — xác nhận].
- Múi giờ để xác định "ngày": cấu hình `app.timezone` (đề xuất `Asia/Ho_Chi_Minh`); dùng `Clock` được inject để test được.
- **Xong khi:** unit test quy tắc sửa trong ngày, dùng đồng hồ giả.

### 4.4 `ReportService` — [BẠN]
- `submit(teamId, content)`: người nộp phải là thành viên nhóm (LEADER hoặc MEMBER). OBSERVER không nộp. Một báo cáo mỗi người mỗi nhóm mỗi ngày; nộp lại cùng ngày → **cập nhật** báo cáo cũ (đề xuất) thay vì lỗi [Mặc định].
- Người nhận theo vai: tác giả là MEMBER → Leader của nhóm; tác giả là LEADER → các Manager.
- Truy vấn:
  - Member xem báo cáo của mình.
  - Leader xem báo cáo của Member nhóm mình lead (theo ngày, lọc người; hiển thị cả người **chưa nộp**).
  - Manager xem báo cáo của Leader.
- **Xong khi:** unit test service qua, gồm test "chưa nộp".

### 4.5 `AccessPolicy` mở rộng — [BẠN]
- `canViewReport(reportId)`: tác giả; Leader của nhóm (nếu tác giả là Member của nhóm đó); Manager (nếu tác giả là Leader của nhóm đó). OBSERVER không xem báo cáo Member.
- `canCommentReport(reportId)`: Leader nhận xét báo cáo Member nhóm mình; Manager nhận xét báo cáo Leader.
- **Xong khi:** test phân quyền theo bảng (vai trò × loại báo cáo) qua.

### 4.6 Nhận xét — [CẢ HAI]
- Thêm nhận xét; xem danh sách nhận xét theo báo cáo; tác giả báo cáo cũng thấy nhận xét. Sửa/xoá nhận xét của chính mình trong thời gian ngắn [Mặc định: chỉ cho thêm, chưa cho sửa/xoá].
- **Xong khi:** thêm và đọc nhận xét đúng quyền.

### 4.7 Controller — [AI]
- `POST /api/v1/teams/{teamId}/reports` (nộp/cập nhật báo cáo hôm nay), `GET /api/v1/teams/{teamId}/reports?date=...` (theo vai trò), `GET /api/v1/reports/mine`, `GET /api/v1/reports/{id}`.
- Nhận xét: `GET|POST /api/v1/reports/{id}/comments`.
- Cho Manager: `GET /api/v1/reports/leaders?date=...`.
- **Xong khi:** Swagger đủ; gọi thử đúng quyền.

### 4.8 Test backend — [TESTER + BẠN]
- Dùng `Clock` giả để test "sửa trong ngày", qua nửa đêm, và múi giờ. Test bảng quyền đầy đủ. Thử phá: đọc báo cáo bằng id đoán, nộp báo cáo vào nhóm không thuộc, nộp ngày khác.
- **Xong khi:** `./gradlew check` xanh.

### 4.9 Frontend — [AI cho giao diện, BẠN cho phần theo vai trò]
- `features/report`:
  - Form báo cáo hôm nay (việc đã làm, kế hoạch, vướng mắc, task liên quan); nạp lại nếu hôm nay đã nộp.
  - "Báo cáo của tôi" theo ngày.
  - Màn hình Leader: báo cáo của nhóm theo ngày, danh sách người **chưa nộp**, khung nhận xét dưới mỗi báo cáo.
  - Màn hình Manager: báo cáo của các Leader (lọc theo nhóm/ngày), nhận xét.
- **Xong khi:** chạy được vòng Member nộp → Leader xem và nhận xét → Leader nộp → Manager xem.

### 4.10 Cổng stage — [REVIEWER, TESTER, BẠN]
- Reviewer, tester thử phá, `scripts/verify-stage.sh 4 --e2e`, chạy thủ công, review PR, tag `stage-4-done`.

## Xong stage khi (tóm tắt)
Member xem được báo cáo của mình, không xem của người khác; Leader chỉ xem báo cáo nhóm mình; Manager xem báo cáo Leader nhưng không xem báo cáo Member; OBSERVER không xem báo cáo Member; nộp lần hai trong ngày xử lý đúng quy ước; test phân quyền đầy đủ.

## Khái niệm Java/Spring sẽ gặp
`java.time` (`LocalDate`, `ZoneId`, `Clock`), ràng buộc unique và upsert, truy vấn "có/không có" (người chưa nộp), kiểm thử với đồng hồ giả, `@Query` có `LEFT JOIN`.
