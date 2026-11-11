# Stage 3 — Task (từng bước)

> Mục tiêu: luồng giao việc, nộp việc, duyệt việc hoàn chỉnh. Xem `IMPLEMENTATION_PLAN.md` Stage 3 và `BUSINESS_OVERVIEW.md` mục 7.
> Điều kiện vào stage: Stage 2 xong (`stage-2-done`).

## Tổng quan thứ tự

`3.1 test case → 3.2 migration → 3.3 máy trạng thái → 3.4 repository → 3.5 TaskService → 3.6 FileStorage → 3.7 nộp file → 3.8 controller → 3.9 test → 3.10 frontend → 3.11 cổng`

Luồng trạng thái: `ASSIGNED → IN_PROGRESS → PENDING_REVIEW (cam) → DONE (xanh)`; `PENDING_REVIEW → NEEDS_REVISION → IN_PROGRESS`.

---

### 3.1 Soạn test case — [AI soạn, BẠN duyệt]
- Phải có: luồng đầy đủ; mỗi chuyển trạng thái **hợp lệ** và **không hợp lệ**; task `require_file` thiếu file không chuyển được; Member không duyệt được; Leader nhóm khác không duyệt được; giao task cho người không thuộc nhóm bị chặn; xung đột phiên bản (409); file sai định dạng/quá dung lượng; Member khác không tải được file.
- **Xong khi:** bạn duyệt.

### 3.2 Migration — [BẠN]
- `task`: `id`, `team_id`, `title`, `description`, `assignee_id`, `assigned_by`, `due_date`, `priority`, `status`, `require_file` (boolean), `revision_reason` (lý do gần nhất khi trả lại), audit, `version`, xoá mềm. Chỉ mục: `(team_id, status)`, `assignee_id`.
- `task_history`: `id`, `task_id`, `from_status`, `to_status`, `changed_by`, `changed_at`, `note`. Chỉ thêm, không sửa.
- `task_submission`: `id`, `task_id`, `file_name`, `content_type`, `size_bytes`, `storage_key`, `uploaded_by`, `uploaded_at`.
- File: `V4__create_task_tables.sql`.
- **Xong khi:** migration chạy, khoá ngoại đúng.

### 3.3 Máy trạng thái trong domain — [BẠN] (phần quan trọng nhất của stage)
- `TaskStatus` (enum) và các phương thức trên `Task`: `start()`, `submitForReview()`, `approve()`, `requestRevision(reason)`. Mỗi phương thức kiểm tra trạng thái hiện tại; sai thì ném `BusinessException`.
- Bảng chuyển hợp lệ:
  - `ASSIGNED → IN_PROGRESS` (assignee)
  - `IN_PROGRESS → PENDING_REVIEW` (assignee; nếu `require_file` thì phải có ít nhất một file nộp)
  - `PENDING_REVIEW → DONE` (Leader của nhóm)
  - `PENDING_REVIEW → NEEDS_REVISION` rồi `→ IN_PROGRESS` (Leader trả lại kèm lý do; mình đề xuất `requestRevision` đặt thẳng về `IN_PROGRESS` và ghi lý do + lịch sử)
- Quy tắc "ai được làm" kiểm tra ở service/`AccessPolicy`, quy tắc "từ trạng thái nào sang trạng thái nào" nằm trong domain.
- **Xong khi:** unit test bảng chuyển (hợp lệ → thành công, không hợp lệ → lỗi) qua.

### 3.4 Repository — [CẢ HAI]
- Task theo nhóm có lọc trạng thái/người phụ trách/hạn chót (whitelist), "việc của tôi", lịch sử theo task, file theo task.
- **Xong khi:** test repository qua.

### 3.5 `TaskService` — [BẠN]
- `create` (Leader của nhóm; người phụ trách phải là MEMBER của chính nhóm đó; nhóm phải đang ACTIVE), `update` (khi chưa DONE), `start`, `submitForReview`, `approve`, `requestRevision`.
- Mỗi lần đổi trạng thái: ghi `task_history` **trong cùng transaction**. Khoá lạc quan trên `version`; xung đột → 409.
- **Xong khi:** unit test service qua; test chứng minh lịch sử được ghi khi đổi trạng thái.

### 3.6 `FileStorage` — [AI viết, BẠN đọc hiểu]
- `common/file`: interface `FileStorage` (`save`, `load`, `delete`), `LocalFileStorage` lưu trong thư mục cấu hình được (ngoài thư mục mã nguồn), tên file lưu là khoá ngẫu nhiên, không dùng tên người dùng đặt.
- Kiểm tra định dạng (PDF, Word, Excel, ảnh, zip) và dung lượng tối đa (mặc định 10 MB) trước khi lưu; chống đường dẫn bất thường (`../`).
- **Xong khi:** test: file hợp lệ lưu và đọc lại được; file sai loại/quá lớn bị từ chối.

### 3.7 Nộp và tải file — [CẢ HAI]
- `TaskSubmissionService`: người phụ trách nộp file khi task ở `IN_PROGRESS`; có thể nộp lại. Tải file: người phụ trách, Leader của nhóm (OBSERVER không tải được file [Mặc định]).
- **Xong khi:** người ngoài nhóm tải file → 403; tải file đúng người → 200 đúng nội dung.

### 3.8 Controller — [AI]
- Danh sách/tạo: `GET|POST /api/v1/teams/{teamId}/tasks`.
- Chi tiết, sửa: `/api/v1/tasks/{id}`. Hành động: `POST /api/v1/tasks/{id}/start`, `/submit-for-review`, `/approve`, `/request-revision` (body có `reason`).
- File: `POST /api/v1/tasks/{id}/submissions` (multipart), `GET /api/v1/tasks/{id}/submissions/{sid}/download`.
- Lịch sử: `GET /api/v1/tasks/{id}/history`.
- **Xong khi:** Swagger đủ; gọi thử luồng đầy đủ bằng công cụ API.

### 3.9 Test backend — [TESTER + BẠN]
- Bám `docs/test-cases/stage-3.md`. Test bảng chuyển trạng thái; bảng quyền (Leader nhóm A × task nhóm B…); file; xung đột phiên bản; thử phá: gọi `approve` khi chưa `PENDING_REVIEW`, gọi hai lần liên tiếp, gửi `status` trực tiếp để sửa.
- **Xong khi:** `./gradlew check` xanh.

### 3.10 Frontend — [AI cho giao diện, BẠN cho logic nút theo vai trò]
- `features/task`:
  - Danh sách task theo nhóm (lọc trạng thái, người phụ trách, hạn chót); chip trạng thái: **cam** cho chờ duyệt, **xanh** cho hoàn thành (màu theo biến CSS).
  - Form giao task (Leader): chọn người phụ trách trong nhóm, hạn chót, ưu tiên, cờ "yêu cầu nộp file".
  - "Việc của tôi" (Member): nút Bắt đầu, Nộp file (thành phần upload có kiểm tra định dạng/dung lượng), Đánh dấu chờ duyệt.
  - Chi tiết task: mô tả, file đã nộp (tải về), **dòng thời gian lịch sử trạng thái**.
  - Màn hình duyệt (Leader): Duyệt / Trả lại (hộp thoại nhập lý do).
- Nút nào hiện theo vai trò và trạng thái (`usePermission` + trạng thái task); backend vẫn là nơi quyết định.
- Xử lý lỗi 409 (có người vừa thay đổi) bằng thông báo nhẹ và tải lại.
- **Xong khi:** chạy trọn luồng Leader giao → Member làm → nộp file → chờ duyệt (cam) → Leader duyệt (xanh) hoặc trả lại có lý do.

### 3.11 Cổng stage — [REVIEWER, TESTER, BẠN]
- Reviewer, tester thử phá, `scripts/verify-stage.sh 3 --e2e` (có ít nhất một luồng E2E: giao → nộp → duyệt), chạy thủ công (màu cam/xanh, upload, lịch sử), review PR, tag `stage-3-done`.

## Xong stage khi (tóm tắt)
Chạy trọn luồng giao – làm – nộp – duyệt/trả lại; task `require_file` không chuyển được nếu thiếu file; Member không duyệt được; mọi chuyển trạng thái có lịch sử; xung đột phiên bản trả 409; toàn bộ test qua.

## Khái niệm Java/Spring sẽ gặp
Enum và máy trạng thái trong domain, `@Transactional` (phạm vi và rollback), `MultipartFile`, xử lý file an toàn, `ResponseEntity` / `Resource` cho tải file, khoá lạc quan và `ObjectOptimisticLockingFailureException`.
