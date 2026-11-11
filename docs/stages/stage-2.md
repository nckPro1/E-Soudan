# Stage 2 — Nhân sự và nhóm (từng bước)

> Mục tiêu: Admin dựng được cơ cấu công ty; Manager xem được. Xem `IMPLEMENTATION_PLAN.md` Stage 2 và `BUSINESS_OVERVIEW.md` mục 4–6.
> Điều kiện vào stage: Stage 1 xong (`stage-1-done`).

## Tổng quan thứ tự

`2.1 test case → 2.2 migration → 2.3 domain → 2.4 repository → 2.5 TeamService → 2.6 AccessPolicy → 2.7 nhân sự → 2.8 controller → 2.9 test → 2.10 frontend → 2.11 cổng`

---

### 2.1 Soạn test case — [AI soạn, BẠN duyệt]
- Phải có: Admin tạo nhóm + chọn Leader; Leader nhóm A **không** sửa được nhóm B; Manager thêm làm OBSERVER; Manager sửa nhân sự → 403; xoá Leader cuối cùng → bị chặn; một người lead hai nhóm; thêm cùng người hai lần → bị chặn; nhóm đã lưu trữ không sửa được.
- **Xong khi:** bạn duyệt.

### 2.2 Migration — [BẠN]
- `team`: `id`, `name` (unique trong số nhóm đang hoạt động), `description`, `status` (ACTIVE/ARCHIVED), cột audit và `version`.
- `team_member`: `id`, `team_id`, `user_id`, `team_role` (LEADER/MEMBER/OBSERVER), `joined_at`, ràng buộc **unique (team_id, user_id)**, khoá ngoại tới `team` và `users`, chỉ mục theo `user_id`.
- File: `V3__create_team_and_member.sql`.
- **Xong khi:** migration chạy, ràng buộc unique hoạt động (thử chèn trùng trong DBeaver).

### 2.3 Domain — [BẠN]
- `team/domain`: `Team`, `TeamMember`, `TeamRole`, `TeamStatus`. Quy tắc nằm trong domain: nhóm đã lưu trữ không thể thay đổi thành viên.
- **Xong khi:** biên dịch qua; có unit test cho quy tắc trong domain.

### 2.4 Repository — [CẢ HAI]
- `TeamRepository` (danh sách nhóm đang hoạt động có phân trang), `TeamMemberRepository` (thành viên theo nhóm, nhóm theo người dùng, đếm Leader của nhóm).
- **Xong khi:** test repository (Testcontainers) qua.

### 2.5 `TeamService` — [BẠN]
- `create(name, leaderId, memberIds, observerIds)`: nhóm phải có **ít nhất một Leader**. Leader/Member phải là tài khoản `USER` (đang ACTIVE); OBSERVER phải có `system_role = MANAGER`; **ADMIN không tham gia nhóm** [Mặc định — xác nhận với chủ dự án].
- `update`, `archive`, `addMember`, `removeMember`, `changeMemberRole`. Không được xoá hoặc hạ vai Leader cuối cùng. Không thêm trùng người.
- Một người có thể là Leader nhiều nhóm, và Member nhóm khác.
- Mọi thao tác trong `@Transactional`; xung đột phiên bản trả 409.
- **Xong khi:** unit test qua cho từng quy tắc ở trên.

### 2.6 `AccessPolicy` mở rộng — [BẠN]
- `isTeamLeader(teamId)`, `isTeamMember(teamId)` (gồm cả Leader), `isTeamObserver(teamId)`, `canViewTeam(teamId)`.
- Lấy user hiện tại từ `CurrentUser`; truy vấn `team_member`.
- **Xong khi:** test phân quyền theo bảng (4 vai trò × thao tác) qua.

### 2.7 Nhân sự (mở rộng module `user`) — [CẢ HAI]
- Tìm kiếm/lọc người dùng theo tên, vị trí, trạng thái, có phân trang (whitelist field). Manager **chỉ xem**, Admin toàn quyền.
- Endpoint danh sách người dùng để chọn thành viên khi tạo nhóm (chỉ trả thông tin tối thiểu: id, tên, vị trí).
- **Xong khi:** Manager gọi API sửa → 403; gọi API danh sách → 200.

### 2.8 Controller — [AI]
- `/api/v1/teams` (tạo, danh sách, chi tiết, sửa, lưu trữ), `/api/v1/teams/{teamId}/members` (thêm, đổi vai, xoá).
- Quyền: tạo/sửa/lưu trữ nhóm và quản lý thành viên chỉ ADMIN; xem chi tiết nhóm cho thành viên, Leader, OBSERVER và Admin.
- **Xong khi:** Swagger UI thể hiện đủ; gọi thử đúng.

### 2.9 Test backend — [TESTER + BẠN]
- Bám `docs/test-cases/stage-2.md`. Thử phá: truy cập chéo nhóm, thêm trùng, xoá Leader cuối, hai thao tác đồng thời cùng sửa nhóm (409).
- **Xong khi:** `./gradlew check` xanh, Modulith `verify()` qua (module `team` không import repository của `user`; dùng interface công khai).

### 2.10 Frontend — [AI cho CRUD, BẠN cho `usePermission`]
- `features/user`: trang **Quản lý nhân sự** (bảng, bộ lọc theo tên/vị trí/trạng thái; nút sửa chỉ hiện với Admin; Manager chỉ xem).
- `features/team`: danh sách nhóm đang hoạt động; form tạo/sửa nhóm (chọn Leader, thêm Member, thêm Manager làm OBSERVER, lưu trữ); trang chi tiết nhóm xem thành viên và vai trò.
- Dùng `useList` và `useForm` đã có từ stage 1; mở rộng nếu thiếu.
- **Xong khi:** Admin dựng được nhóm đầy đủ; Manager đăng nhập chỉ xem, không thấy nút sửa; USER thấy các nhóm mình tham gia.

### 2.11 Cổng stage — [REVIEWER, TESTER, BẠN]
- Reviewer, tester thử phá, `scripts/verify-stage.sh 2`, chạy thủ công, review PR, tag `stage-2-done`.

## Xong stage khi (tóm tắt)
Admin tạo nhóm, chọn Leader, thêm Member và OBSERVER; Manager xem được nhân sự nhưng không sửa (403); một người là Leader nhóm A và Member nhóm B hoạt động đúng; nhóm luôn có ít nhất một Leader.

## Khái niệm Java/Spring sẽ gặp
Quan hệ JPA nhiều-nhiều qua bảng trung gian, `@ManyToOne` và tải lười, truy vấn `@Query`, ràng buộc unique và xử lý `DataIntegrityViolationException`, kiểm tra quyền theo dữ liệu (`@PreAuthorize` + bean).
