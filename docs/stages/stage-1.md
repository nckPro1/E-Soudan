# Stage 1 — Nền tảng và đăng nhập (từng bước)

> Mục tiêu: đăng nhập được, có khung chung (`common/`) để mọi module sau dùng lại. Xem `IMPLEMENTATION_PLAN.md` Stage 1.
> Điều kiện vào stage: Stage 0 xong (`SETUP.md`), ERD đã có bảng `users`.

## Tổng quan thứ tự

`1.1 test case → 1.2 phụ thuộc → 1.3 migration → 1.4 BaseEntity → 1.5 API/lỗi → 1.6 tìm kiếm → 1.7 user → 1.8 auth/JWT → 1.9 quyền → 1.10 seed Admin → 1.11 controller → 1.12 test → 1.13 frontend → 1.14 cổng`

---

### 1.1 Soạn test case — [AI soạn, BẠN duyệt]
- Tạo `docs/test-cases/stage-1.md` từ mẫu. Phải có: đăng nhập đúng/sai, tài khoản bị khoá, không token → 401, USER gọi API Admin → 403, tạo user trùng email → 409/422, đổi mật khẩu sai mật khẩu cũ.
- **Xong khi:** bạn đã duyệt file.

### 1.2 Thêm phụ thuộc — [AI]
- Thêm vào `backend/build.gradle.kts`: Spring Security, thư viện JWT, MapStruct (kèm annotation processor), springdoc-openapi, Spring Modulith (core + test).
- **Xong khi:** `./gradlew check` vẫn qua. Lưu ý: từ giờ mọi endpoint bị Security chặn mặc định; phần cấu hình ở 1.8.

### 1.3 Migration `users` — [BẠN]
- Cột đề xuất: `id` (bigint identity), `email` (unique, không phân biệt hoa thường), `password_hash`, `full_name`, `position` (text, nhập tự do), `system_role` (ADMIN/MANAGER/USER), `status` (ACTIVE/LOCKED), `must_change_password` (boolean), cùng cột audit: `created_at`, `created_by`, `updated_at`, `updated_by`, `version`, `deleted_at`.
- Quyết định cần chốt ở ERD: lưu **refresh token** thế nào. Phương án đơn giản: bảng `refresh_token` (user_id, token_hash, expires_at, revoked_at). Đây là bảng thứ 13; cập nhật `ARCHITECTURE.md` nếu chọn.
- Đặt tên file `V2__create_users.sql` (V1 là baseline).
- **Xong khi:** backend khởi động, Flyway chạy, bảng có trong DB (xem bằng DBeaver).

### 1.4 `BaseEntity` và auditing — [BẠN viết, AI giải thích khái niệm]
- `common/base/BaseEntity`: `id`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `version` (`@Version`), `deletedAt`; `@MappedSuperclass`, dùng `@EntityListeners(AuditingEntityListener.class)`.
- Xoá mềm: `@SQLRestriction("deleted_at IS NULL")` trên entity cần xoá mềm.
- `config/JpaAuditingConfig`: `@EnableJpaAuditing` + `AuditorAware` lấy id người dùng hiện tại (hiện tại trả rỗng, nối với Security ở 1.8).
- **Xong khi:** test unit/slice tạo một entity thử, kiểm tra `createdAt` và `version` được điền.

### 1.5 Phản hồi và xử lý lỗi — [AI viết, BẠN đọc hiểu]
- `common/api`: `ApiResponse<T>`, `PageResponse<T>`, `PageQuery`, `ErrorCode` (enum).
- `common/exception`: `BusinessException`, `NotFoundException`, `ForbiddenException`, `ConflictException`, `GlobalExceptionHandler` (`@RestControllerAdvice`) ánh xạ sang HTTP 400/401/403/404/409/422/500 theo `ARCHITECTURE.md` mục 5.2.
- **Xong khi:** test controller thử trả đúng JSON lỗi chuẩn cho từng loại exception; lỗi validation trả 422 kèm danh sách field.

### 1.6 Lọc, sắp xếp, phân trang — [CẢ HAI]
- `common/search`: `SearchSpecification` dựng JPA `Specification` từ filter; **mỗi module khai báo whitelist** field cho lọc/sắp xếp. Giới hạn `size` tối đa (ví dụ 100). Không nối chuỗi từ request.
- **Xong khi:** test chứng minh field ngoài whitelist bị từ chối (422), và sắp xếp theo field hợp lệ hoạt động.

### 1.7 Module `user` (domain → service) — [BẠN]
- `user/domain`: `User`, `SystemRole`, `UserStatus`.
- `user/repository`: `UserRepository` (tìm theo email không phân biệt hoa thường, tìm kiếm phân trang).
- `user/service`: `UserService` — tạo tài khoản (Admin), sửa hồ sơ (gồm `position`), gán role MANAGER, khoá/mở khoá, đổi mật khẩu; mật khẩu băm BCrypt. Quy tắc: email duy nhất; Admin không tự khoá/hạ quyền mình khi là Admin cuối cùng.
- **Xong khi:** unit test service qua (mock repository); quy tắc "Admin cuối cùng" có test.

### 1.8 Module `auth` và JWT — [CẢ HAI]
- `JwtService`: tạo/kiểm tra access token (ngắn hạn), refresh token. Khoá bí mật lấy từ biến môi trường.
- `AuthService`: `login` (kiểm tra mật khẩu, trạng thái ACTIVE), `refresh`, `logout` (thu hồi refresh token nếu có bảng).
- `config/SecurityConfig`: stateless, bộ lọc JWT, cho phép `/auth/**`, `/actuator/health`, OpenAPI; còn lại cần đăng nhập. Nối `AuditorAware` với người dùng hiện tại.
- Phản hồi 401/403 dùng cùng định dạng `ApiResponse` lỗi.
- **Xong khi:** gọi API cần quyền không kèm token → 401; token hết hạn → 401; token hợp lệ → vào được.

### 1.9 `CurrentUser`, `AccessPolicy`, `/me` — [BẠN]
- `common/security/CurrentUser` (lấy user/ role hiện tại), `AccessPolicy` (khung; thêm hàm theo từng stage), bật `@EnableMethodSecurity`.
- `GET /api/v1/me`, `GET /api/v1/me/permissions` (trả danh sách quyền dạng chuỗi/cờ cho frontend).
- **Xong khi:** test: USER chỉ thấy quyền USER, ADMIN thấy quyền Admin.

### 1.10 Tạo tài khoản Admin đầu tiên — [BẠN]
- Khi khởi động profile `dev`, nếu chưa có Admin: tạo từ biến môi trường (email, mật khẩu tạm), `must_change_password = true`. Không đặt mật khẩu cứng trong code.
- **Xong khi:** khởi động lần đầu tạo Admin; lần sau không tạo trùng.

### 1.11 Controller — [AI]
- `AuthController` (`/api/v1/auth/login|refresh|logout`), `UserController` (`/api/v1/users`: tạo, sửa, khoá, đổi role MANAGER, danh sách phân trang; chỉ ADMIN), `POST /api/v1/me/change-password`.
- DTO là `record`, ánh xạ bằng MapStruct. Controller mỏng, chỉ gọi service.
- **Xong khi:** Swagger UI hiển thị đúng; gọi thử bằng công cụ API cho kết quả đúng.

### 1.12 Test backend — [TESTER + BẠN]
- Unit: `UserService`, `AuthService`. Integration (Testcontainers): luồng login → gọi API bảo vệ; lỗi 401/403; tạo user trùng email.
- Test phân quyền dạng bảng: mỗi endpoint × mỗi vai trò.
- Test cấu trúc: Spring Modulith `verify()` qua.
- **Xong khi:** `./gradlew check` xanh.

### 1.13 Frontend — [AI cho khung, BẠN cho `useForm`/`usePermission`]
- `shared/api`: axios instance, interceptor gắn token, xử lý 401 (làm mới token rồi thử lại, thất bại thì về đăng nhập), xử lý lỗi chung.
- Pinia `auth` store: user, token, `permissions`; `usePermission`.
- Router guard: chưa đăng nhập → `/login`; `must_change_password` → trang đổi mật khẩu.
- Trang `login` và `change-password` theo thiết kế Stitch (`docs/design/`); khung bố cục sau đăng nhập (thanh bên + header).
- `useList`, `useForm` bản đầu; trang **Quản lý tài khoản** (Admin): danh sách, tạo, sửa, khoá.
- Biến CSS màu theo `docs/design/`.
- **Xong khi:** đăng nhập bằng Admin, thấy danh sách tài khoản, tạo được tài khoản USER; đăng xuất; USER không vào được trang Admin.

### 1.14 Cổng stage — [REVIEWER, TESTER, BẠN]
- Reviewer review → tester thử phá (sai token, khoá tài khoản giữa phiên, tạo user bằng token USER…) → `scripts/verify-stage.sh 1` → bạn chạy thủ công các case MANUAL → review PR → tag `stage-1-done`.

## Xong stage khi (tóm tắt)
Admin đăng nhập, tạo tài khoản USER và MANAGER; USER đăng nhập được; không token → 401; USER gọi API Admin → 403; các test trên qua; `verify-stage.sh 1` qua.

## Khái niệm Java/Spring sẽ gặp (để học)
`@MappedSuperclass`, JPA auditing, `@Version` (khoá lạc quan), `Specification`, `@RestControllerAdvice`, Spring Security filter chain, `PasswordEncoder` (BCrypt), `@PreAuthorize`, Bean Validation, Testcontainers, MockMvc.
