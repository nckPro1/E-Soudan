# ARCHITECTURE — Kiến trúc kỹ thuật

> Đọc cùng `BUSINESS_OVERVIEW.md` (nghiệp vụ). Tài liệu này mô tả **cách tổ chức code**; không thêm tính năng ngoài nghiệp vụ.
> Tên dự án chưa chốt: dùng `<project>` làm placeholder. Package Java phải viết thường, không gạch ngang (ví dụ `com.example.tasuki`).
> Các mục đánh dấu **[Mặc định]** là lựa chọn tạm do chưa được chủ dự án xác nhận. Nếu cần đổi, hỏi lại.

---

## 1. Mục tiêu kiến trúc

- Code sạch, giống cách một công ty IT làm thật: phân tầng rõ, tái sử dụng cao, thêm module mới dễ, ít lặp code.
- Chủ dự án đang luyện lại Java; ưu tiên **dễ đọc và dễ giải thích** hơn là "thông minh".
- Học theo tư duy của dự án mẫu E-project (tách `models` / `libs` / `rules` / `views` / `mixins`), nhưng viết lại cho Java + Vue 3 và sửa các điểm yếu đã biết (xem mục 15).

## 2. Quyết định kiến trúc

| Chủ đề | Quyết định |
|---|---|
| Kiểu kiến trúc | **Modular monolith**: một ứng dụng triển khai một khối, chia module theo nghiệp vụ |
| Cách chia package | **Package-by-feature**; bên trong mỗi module vẫn phân tầng controller → service → repository |
| Kiểm tra ranh giới module | **[Mặc định]** Quy ước + một test kiểm tra cấu trúc bằng Spring Modulith (`ApplicationModules.verify()`) |
| Xác thực | **[Mặc định]** JWT (access token ngắn hạn + refresh token) |
| Build tool | **Gradle** (Kotlin DSL, dùng Gradle Wrapper) — chủ dự án đã chốt |
| Ngôn ngữ frontend | **[Mặc định]** TypeScript |
| Thư viện UI frontend | **PrimeVue** (theme Aura chỉnh theo design system) — chủ dự án đã chốt |
| Kiểu khóa chính | **[Mặc định]** `BIGINT` identity |
| Đa tenant | Không. Một công ty duy nhất. |

## 3. Công nghệ

**Backend:** Java 21 (LTS), Spring Boot (bản ổn định mới nhất lúc khởi tạo, kiểm tra trên start.spring.io), Spring Web, Spring Data JPA, Spring Security, Bean Validation, Spring WebSocket (STOMP), Spring Mail, Actuator, MapStruct, springdoc-openapi, Flyway, Lombok (dùng hạn chế).

**Frontend:** Vue 3 (Composition API, `<script setup>`), Vite, TypeScript, Pinia, Vue Router 4, axios, `@stomp/stompjs`, vue-i18n (để dành cho tiếng Nhật/Việt).

**Dữ liệu và hạ tầng:** PostgreSQL, Docker, AWS (xem mục 14).

**Kiểm thử:** JUnit 5, Mockito, Testcontainers (PostgreSQL), Spring Modulith test.

## 4. Backend — cấu trúc package

```
com.example.<project>/
├── Application.java
├── config/                  # cấu hình dùng chung
│   ├── SecurityConfig, JwtConfig
│   ├── WebSocketConfig
│   ├── JpaAuditingConfig    # AuditorAware lấy user hiện tại
│   ├── AsyncConfig, WebConfig (CORS), OpenApiConfig
├── common/                  # tương đương api/libs của E-project (generic, không chứa nghiệp vụ)
│   ├── base/                # BaseEntity, AbstractCrudService
│   ├── api/                 # ApiResponse<T>, PageResponse<T>, PageQuery, ErrorCode
│   ├── exception/           # BusinessException, NotFoundException, ForbiddenException, ConflictException, GlobalExceptionHandler
│   ├── search/              # SearchSpecification, whitelist field lọc/sắp xếp
│   ├── security/            # CurrentUser, AccessPolicy
│   ├── file/                # FileStorage (interface), LocalFileStorage, S3FileStorage
│   └── util/
├── auth/                    # đăng nhập, cấp/đổi token
├── user/                    # tài khoản, hồ sơ nhân sự
├── team/                    # team, team_member
├── task/                    # task, task_history, task_submission
├── report/                  # daily_report, report_comment
├── chat/                    # conversation, conversation_member, chat_message
└── notification/            # notification, gửi mail
```

### 4.1 Cấu trúc bên trong một module

```
task/
├── web/          TaskController, các record request/response (DTO)
├── service/      TaskService  (@Transactional ở đây)
├── domain/       Task, TaskStatus, TaskHistory  (entity + quy tắc trạng thái)
├── repository/   TaskRepository
├── mapper/       TaskMapper (MapStruct)
├── event/        TaskAssignedEvent, TaskReviewedEvent ...
└── TaskApi.java  (tuỳ chọn) interface công khai cho module khác gọi
```

Với Spring Modulith: type nằm ở **package gốc của module** (ví dụ `task/TaskApi`, `task/event/...` nếu khai báo công khai) mới được module khác dùng; các sub-package còn lại là nội bộ.

### 4.2 Quy tắc phụ thuộc (bắt buộc)

1. `Controller` chỉ gọi `Service`. Không gọi repository, không trả entity ra ngoài; luôn trả DTO.
2. Nghiệp vụ nằm ở `Service`/`domain`, không nằm ở controller.
3. Module A **không** import entity hoặc repository của module B. Cần dữ liệu thì gọi qua interface công khai của B, hoặc phản ứng với sự kiện (event).
4. `common/` không phụ thuộc vào module nghiệp vụ nào.
5. Không có vòng phụ thuộc giữa các module.
6. DTO dùng Java `record`; ánh xạ entity ↔ DTO bằng MapStruct.

## 5. Tầng dùng chung (`common/`)

### 5.1 `BaseEntity`
Các cột: `id`, `created_at`, `created_by`, `updated_at`, `updated_by`, `version` (`@Version`, khóa lạc quan), `deleted_at` (xóa mềm, kèm `@SQLRestriction`). Thời gian lưu `timestamptz`, kiểu Java `Instant`, múi giờ UTC; client tự hiển thị theo múi giờ của người dùng.

Khóa lạc quan: response trả `version`, client gửi lại khi cập nhật; xung đột trả HTTP 409.

### 5.2 `ApiResponse<T>` và lỗi
- Thành công: `{ "success": true, "data": ... }`.
- Lỗi: `{ "success": false, "code": "TASK_NOT_FOUND", "message": "...", "details": [...], "timestamp": "..." }`.
- Dùng đúng HTTP status (400, 401, 403, 404, 409, 422, 500). `ErrorCode` là enum tập trung.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) gom toàn bộ xử lý lỗi. Không bắt exception rải rác trong controller.

### 5.3 Phân trang, lọc, sắp xếp
- `PageQuery`: `page`, `size`, `sort`, `filters`. `PageResponse<T>`: `items`, `page`, `size`, `totalItems`, `totalPages`.
- **Chỉ cho phép field nằm trong whitelist** do từng module khai báo. Không bao giờ nối chuỗi từ request vào câu truy vấn. Dùng JPA `Specification`.
- Giới hạn `size` tối đa (ví dụ 100).

### 5.4 `AbstractCrudService<E, ID>`
Chỉ cung cấp thao tác CRUD thuần (tạo, đọc, sửa, xóa mềm, tìm kiếm phân trang). Dùng cho module đơn giản như `user`, `team`. Module có nghiệp vụ riêng (task, report, chat) viết service riêng. **Không** tạo controller CRUD tổng quát cho mọi thực thể.

### 5.5 `FileStorage`
Interface `save`, `load`, `delete`. MVP dùng `LocalFileStorage` (đĩa server); production dùng `S3FileStorage`. Nghiệp vụ chỉ biết interface. Kiểm tra định dạng và dung lượng trước khi lưu (xem `BUSINESS_OVERVIEW.md` mục 13).

## 6. CSDL

- PostgreSQL; schema quản lý bằng **Flyway**, file `src/main/resources/db/migration/V<n>__<mo_ta>.sql`. Không dùng `spring.jpa.hibernate.ddl-auto=update` ngoài môi trường thử nghiệm cục bộ. Không sửa migration đã chạy; thêm migration mới.
- Tên bảng/cột `snake_case`. **`user` là từ khóa dành riêng của PostgreSQL; đặt bảng người dùng là `users`** (các bảng còn lại đặt theo `BUSINESS_OVERVIEW.md`).
- Khóa ngoại và chỉ mục khai báo rõ trong migration.
- Mọi thay đổi trạng thái task ghi vào `task_history` trong **cùng transaction** với thay đổi chính.

## 7. Bảo mật và phân quyền

- Spring Security + JWT. Mật khẩu băm bằng BCrypt.
- **Hai tầng vai trò** (xem `BUSINESS_OVERVIEW.md` mục 3): `users.system_role` (ADMIN, MANAGER, USER) và `team_member.team_role` (LEADER, MEMBER, OBSERVER).
- Kiểm tra quyền tập trung ở `AccessPolicy` (bean), ví dụ `isTeamLeader(teamId)`, `isTeamMember(teamId)`, `canViewReport(reportId)`. Dùng qua `@PreAuthorize("@accessPolicy.isTeamLeader(#teamId)")` và gọi lại trong service khi cần.
- **Quyền được kiểm tra ở backend là bắt buộc.** Frontend chỉ ẩn/hiện giao diện, lấy quyền qua API (ví dụ `GET /api/v1/me/permissions`).
- DM chỉ hai người trong cuộc đọc được; truy vấn tin nhắn luôn lọc theo `conversation_member` của người dùng hiện tại.
- WebSocket: xác thực token khi bắt tay kết nối; kiểm tra người dùng thuộc phòng trước khi cho đăng ký kênh.
- Không đưa bí mật (JWT secret, mật khẩu DB/SMTP) vào code hoặc git; dùng biến môi trường hoặc AWS Secrets Manager.

## 8. Quy ước API

- Tiền tố `/api/v1`. REST, danh từ số nhiều: `/api/v1/teams/{teamId}/tasks`.
- Dùng đúng phương thức HTTP; thay đổi trạng thái bằng endpoint hành động rõ ràng (ví dụ `POST /tasks/{id}/submit-for-review`, `POST /tasks/{id}/approve`, `POST /tasks/{id}/request-revision`), không sửa trực tiếp trường `status`.
- Request dùng Bean Validation; lỗi validation trả 422 kèm danh sách field lỗi.
- Tài liệu API sinh tự động bằng springdoc-openapi.

## 9. Sự kiện và thông báo

- Service phát sự kiện miền (ví dụ `TaskAssignedEvent`) bằng `ApplicationEventPublisher`.
- Module `notification` lắng nghe bằng `@TransactionalEventListener(phase = AFTER_COMMIT)` kèm `@Async` (hoặc `@ApplicationModuleListener` nếu dùng Spring Modulith). Nhờ vậy: chỉ gửi sau khi dữ liệu đã lưu thành công, và lỗi gửi mail không làm hỏng nghiệp vụ chính.
- Lưu bản ghi `notification` trong DB; mail là kênh gửi bổ sung. Có trạng thái gửi để thử lại khi lỗi (giai đoạn sau).

## 10. Chat

- Dùng chung `conversation` (loại `GROUP`/`DIRECT`) cho chat nhóm và DM.
- Lộ trình: (1) REST + polling → (2) WebSocket + STOMP, kênh `/topic/conversations/{id}` → (3) tin chưa đọc (`last_read_message_id`), trạng thái online.
- Tin nhắn luôn **lưu DB trước**, rồi mới đẩy qua WebSocket.

## 11. Frontend

```
src/
├── app/            # main.ts, router gốc, plugins (pinia, i18n)
├── shared/
│   ├── api/        # axios instance, interceptor (gắn token, xử lý lỗi chung, refresh token)
│   ├── components/ # BaseTable, BaseForm, BaseModal, các field dùng chung
│   ├── composables/# useList, useForm, usePermission
│   ├── types/
│   └── utils/
└── features/
    ├── auth/  user/  team/  task/  report/  chat/  notification/
        ├── views/        # trang
        ├── components/   # component riêng của feature
        ├── api.ts        # gọi API của feature
        ├── store.ts      # Pinia store (nếu cần)
        └── routes.ts     # route của feature, router gốc import vào
```

Quy tắc:
- Nhóm theo **feature**, không nhóm theo loại file. Code dùng chung đặt ở `shared/`.
- Component chỉ gọi hàm trong `api.ts` của feature, không gọi axios trực tiếp.
- `useList` (phân trang, lọc, sắp xếp) và `useForm` (nạp dữ liệu, kiểm tra, lưu, xử lý xung đột `version`) là lõi tái sử dụng, tương ứng `list_controller` và `form_controller` của E-project.
- Router guard kiểm tra đăng nhập và quyền (từ `usePermission`).
- Văn bản giao diện đi qua vue-i18n ngay từ đầu (dù ban đầu chỉ có một ngôn ngữ).

## 12. Kiểm thử

- Service: unit test (Mockito) cho quy tắc nghiệp vụ, đặc biệt luồng trạng thái task và kiểm tra quyền.
- Repository và tích hợp: Testcontainers với PostgreSQL thật.
- Cấu trúc: test Spring Modulith `ApplicationModules.of(Application.class).verify()` chạy trong CI.
- Frontend: Vitest cho composable; Vue Test Utils cho component quan trọng.

## 13. Cấu hình môi trường

- Profile Spring: `dev`, `test`, `prod`. File `application.yml` chung + `application-<profile>.yml`.
- Chạy cục bộ: `docker-compose.yml` gồm PostgreSQL (và công cụ xem mail thử như MailHog/Mailtrap).
- Logging dạng có cấu trúc ở `prod`; bật Actuator health.

## 14. Triển khai (AWS)

Lộ trình theo chi phí và độ phức tạp:

1. **Cục bộ:** Docker Compose.
2. **Giai đoạn đầu trên AWS (tuỳ chọn, rẻ hơn):** một EC2 chạy Docker Compose.
3. **Kiến trúc đích:**
   - Frontend: build thành file tĩnh, đặt ở **S3 + CloudFront**.
   - Backend: Docker image trong **ECR**, chạy **ECS Fargate** sau **Application Load Balancer**. Đặt `-Xmx` khoảng 75% bộ nhớ container; health check dùng Actuator.
   - CSDL: **RDS PostgreSQL** trong subnet riêng.
   - File nộp: **S3** (thông qua `S3FileStorage`).
   - Log: CloudWatch. Bí mật: Secrets Manager hoặc biến môi trường.
   - CI/CD: GitHub Actions (build, test, đẩy image, deploy).
4. WebSocket qua ALB: kiểm tra cấu hình timeout kết nối rảnh.
5. Chi phí (ALB, NAT Gateway, RDS tính theo giờ) cần kiểm tra trước khi bật.

## 15. Đối chiếu với E-project và các bài học

| E-project | Dự án này |
|---|---|
| `api/models/*.js` (route + DB + nghiệp vụ trong một file) | Module: controller / service / repository tách riêng |
| `api/libs/` | `common/` |
| `base_model.js` (`base_set`, `base_delete`, datasource) | `BaseEntity`, `AbstractCrudService`, `SearchSpecification`, `PageResponse` |
| `response_utils` | `ApiResponse` + `GlobalExceptionHandler` |
| `rules/acl_rules.js` (dùng chung hai phía) | `AccessPolicy` ở backend + API quyền cho frontend |
| `db_info` + `table_validation` (tự đồng bộ bảng) | Flyway migration |
| `mixins/` (list, form controller) | composables `useList`, `useForm` |
| `libs/ajax.js` | `shared/api` (axios) |
| `views/`, `components/` | `features/*/views`, `shared/components` |

Điểm yếu cần tránh (đã ghi nhận từ E-project): nối chuỗi từ request vào SQL (dùng whitelist + Specification), xác nhận xóa yếu kiểu `seq_id * 3` (dùng kiểm tra quyền thật), callback lồng nhau (dùng service tuần tự + `@Transactional`), thiếu lịch sử khi xóa (xóa mềm + lịch sử), log mọi điều kiện lọc mỗi request.

## 16. Quy trình thêm một module mới

1. Thêm migration Flyway cho bảng.
2. Tạo package module với `domain`, `repository`, `service`, `web`, `mapper` (và `event` nếu cần).
3. Entity kế thừa `BaseEntity`; service kế thừa `AbstractCrudService` nếu là CRUD thuần.
4. Khai báo whitelist field lọc/sắp xếp.
5. Khai báo quyền trong `AccessPolicy`.
6. Viết controller trả `ApiResponse`, dùng DTO record + MapStruct.
7. Frontend: tạo `features/<ten>/` với `routes.ts`, `api.ts`, view dùng `useList`/`useForm`.
8. Viết test cho service; chạy test kiểm tra cấu trúc module.

## 17. Quy tắc cho Claude Code

- Tuân thủ mục 4.2 (quy tắc phụ thuộc) và mục 7 (quyền ở backend). Không đi tắt.
- Không tự thêm thư viện hoặc tính năng ngoài tài liệu này và `BUSINESS_OVERVIEW.md`; cần thêm thì hỏi.
- Mỗi lần thêm module, đi theo mục 16.
- Không sửa migration đã tồn tại; thêm migration mới.
- Không viết chuỗi truy vấn nối từ dữ liệu người dùng.
- Không đưa bí mật vào code hay git.
- Chủ dự án đang luyện lại Java: code phải rõ ràng, đặt tên nhất quán, comment ngắn chỗ không hiển nhiên.
- Điều gì còn đánh dấu **[Mặc định]** thì coi là chưa chốt: hỏi trước khi xây thứ phụ thuộc vào nó.
