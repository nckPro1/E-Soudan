# SETUP — Dựng khung dự án (Stage 0)

> Stage 0 đã làm xong theo `ARCHITECTURE.md`. Tài liệu này ghi lại **đã dựng gì**, **chạy thế nào** và các **lựa chọn đã chốt**.

## 1. Lựa chọn đã chốt

| Chủ đề | Giá trị |
|---|---|
| Tên dự án / package gốc | `soudan` / `com.soudan` |
| Build backend | **Gradle** (Kotlin DSL), Gradle Wrapper |
| Spring Boot | 4.1.2 (bản ổn định mới nhất lúc khởi tạo) |
| Java | toolchain 21 (LTS); JDK cài trên máy có thể mới hơn |
| Spring Modulith | 2.1.1 |
| MapStruct / springdoc | 1.6.3 / 3.2.0 |
| PostgreSQL (dev, test) | 16 (Docker) |
| Frontend | Vue 3 + Vite + TypeScript, Pinia, Vue Router, axios, vue-i18n, `@stomp/stompjs`, Vitest |
| Thư viện UI | **PrimeVue 4.5.x** (MIT, ghim `~4.5.5`) + `@primeuix/themes ~2.0.3` + `primeicons`; cấu hình ở `frontend/src/app/plugins/primevue.ts`. **Không nâng lên PrimeVue 5**: bản 5 là giấy phép thương mại, cần khoá license, thiếu khoá thì hiện nhãn đỏ "Invalid PrimeUI License" (xem `node_modules/primevue/LICENSE.md`). |

## 2. Cần cài trên máy

- JDK 21 trở lên (Gradle tự chọn toolchain 21 nếu có sẵn hoặc tải được)
- Node.js 22+ và npm
- Docker Desktop (cho PostgreSQL, MailHog và Testcontainers)
- Git

Không cần cài Gradle hay PostgreSQL riêng: dùng `./gradlew` và Docker.

## 3. Chạy cục bộ

```bash
# 1. Hạ tầng: PostgreSQL (5432) và MailHog (SMTP 1025, giao diện http://localhost:8025)
docker compose up -d

# 2. Backend (profile dev mặc định) — http://localhost:8080
#    Lần đầu cần đặt Admin đầu tiên (tạo khi chưa có Admin nào; phải đổi mật khẩu khi đăng nhập lần đầu):
#    bash:       export ADMIN_EMAIL=admin@soudan.local ADMIN_TEMP_PASSWORD='<mật khẩu tạm >= 8 ký tự>'
#    PowerShell: $env:ADMIN_EMAIL='admin@soudan.local'; $env:ADMIN_TEMP_PASSWORD='<mật khẩu tạm>'
cd backend
./gradlew bootRun

# 3. Frontend — http://localhost:5173 (proxy /api sang :8080)
cd frontend
npm install
npm run dev
```

Kiểm tra backend: `http://localhost:8080/actuator/health`.

> Từ Stage 1 mọi endpoint (trừ `/auth/**`, health, OpenAPI) cần đăng nhập. Swagger UI: `http://localhost:8080/swagger-ui.html`
> (bấm Authorize, dán access token lấy từ `POST /api/v1/auth/login`).

## 4. Chạy test

```bash
cd backend  && ./gradlew check          # unit + tích hợp (Testcontainers cần Docker chạy) + Modulith verify
cd frontend && npm run lint && npm run typecheck && npm run test
scripts/verify-stage.sh <N>             # cổng tự động của stage (xem TESTING_STRATEGY.md)
```

## 5. Cấu trúc thư mục

```
E-soudan/
├── backend/            Spring Boot (Gradle), package com.soudan
│   └── src/main/java/com/soudan/
│       ├── Application.java
│       ├── config/     cấu hình dùng chung
│       ├── common/     base, api, exception, search, security, file, util
│       └── auth user team task report chat notification   (mỗi module: web service domain repository mapper event)
├── frontend/           Vue 3 (app/, shared/, features/*)
├── e2e/                Playwright (chưa dựng; tới Stage 3)
├── design/             tham chiếu thiết kế Stitch
├── docs/               tài liệu, test-cases/, reviews/, stages/
├── scripts/            verify-stage.sh
├── docker-compose.yml
└── .env.example
```

Các package module hiện chỉ có `package-info.java`; code nghiệp vụ được thêm theo từng stage.

## 6. Profile và cấu hình

- `dev` (mặc định): kết nối PostgreSQL/MailHog của `docker-compose.yml`.
- `test`: datasource do Testcontainers cấp.
- `prod`: mọi bí mật đến từ biến môi trường (xem `.env.example`); thiếu biến thì khởi động thất bại.
- Schema do **Flyway** quản lý (`backend/src/main/resources/db/migration`); `ddl-auto=validate`. `V1__baseline.sql` chứa bảng `event_publication` của Spring Modulith; bảng `users` là `V2` ở Stage 1.

## 7. Kiểm chứng khung (đã chạy)

- `./gradlew check`: qua, gồm `contextLoads` (PostgreSQL thật qua Testcontainers) và `ModularityTests` (`ApplicationModules.verify()`).
- Frontend: `lint`, `typecheck`, `test`, `build` đều qua.

## 8. Còn lại cho Stage 0 / việc cần bạn làm

- Tạo `BUSINESS_OVERVIEW.md` và `docs/erd.md` (Stage 1 cần ERD có bảng `users`).
- Cân nhắc thêm CI (`.github/workflows/ci.yml`), `e2e/` (Playwright) và `CLAUDE.md`.
- Nội dung thiết kế Stitch nằm ở `design/login`; `stage-1.md` nhắc `docs/design/` — hai chỗ này cần thống nhất.

## 9. Lưu ý khi làm frontend

- **Chữ Nhật/Việt:** font Noto Sans JP và Noto Serif JP tải từ Google Fonts (`index.html`), cần mạng khi chạy.
- **Ngôn ngữ mặc định `vi`** (`frontend/src/app/plugins/i18n.ts`); người dùng đổi được ở góc trên phải, lựa chọn được nhớ trong `localStorage`.
- **Phiên đăng nhập:** access token giữ trong bộ nhớ; refresh token lưu `localStorage` (backend trả qua JSON). Đây là đánh đổi chấp nhận được cho MVP; muốn chắc hơn thì chuyển sang cookie `HttpOnly` ở backend.
- **Chuỗi i18n không được chứa ký tự `@`** (vue-i18n hiểu là cú pháp "linked message"); muốn hiện `@` dùng `{'@'}` hoặc để ngoài file ngôn ngữ.
- **Windows:** nếu `npm run test` báo `Cannot read properties of undefined (reading 'config')` thì chạy từ PowerShell/cmd thay vì Git Bash (đã gặp trong môi trường tự động hoá; ở terminal thường chưa thấy).

## Email thông báo

- Khi **task được giao** (tạo mới hoặc đổi người phụ trách) thì Member nhận email; khi **Member đánh dấu chờ duyệt** thì mọi Leader của nhóm nhận email. Nội dung tiếng Việt, kèm đường dẫn tới task.
- Email gửi sau khi giao dịch đã commit, trên luồng riêng (`@ApplicationModuleListener`): lỗi gửi mail không làm hỏng thao tác của người dùng. Sự kiện gửi lỗi được giữ trong bảng `event_publication` và gửi lại khi khởi động lại ứng dụng.
- Dev: mọi email đi vào **MailHog**, xem tại http://localhost:8025 (không gửi ra ngoài). Không có `spring.mail.host` thì chỉ ghi log.
- Cấu hình: `MAIL_FROM` (người gửi), `FRONTEND_URL` (địa chỉ web dùng dựng link, mặc định `http://localhost:5173`); máy chủ SMTP thật qua `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.
- **Bình luận mới** (kể cả trả lời) báo cho người phụ trách và các Leader, trừ người viết; tác giả của bình luận được trả lời nhận thư riêng. Sửa bình luận và thả biểu cảm không gửi email.
- Người dùng chưa có tuỳ chọn ngôn ngữ email hay tắt thông báo; làm sau nếu cần.

## Biểu cảm (emoji)

Dùng `emoji-picker-element` (Apache-2.0, web component) và dữ liệu `emoji-picker-element-data`, **nhúng sẵn trong bản build** (không gọi CDN). Cả hai chỉ tải khi người dùng mở "Thêm..." trong bảng biểu cảm. Nhãn của bảng chọn đầy đủ (ô tìm kiếm, tên nhóm) hiện bằng tiếng Anh; cần thì truyền thêm bản dịch qua thuộc tính `i18n` của thư viện.

### Gửi email thật (thay vì MailHog)

Đặt biến môi trường **trước khi chạy backend** (không ghi mật khẩu vào file nào trong repo), ví dụ Gmail:

```powershell
$env:MAIL_HOST='smtp.gmail.com'; $env:MAIL_PORT='587'
$env:MAIL_USERNAME='ban@gmail.com'; $env:MAIL_PASSWORD='<mật khẩu ứng dụng 16 ký tự>'
$env:MAIL_SMTP_AUTH='true'; $env:MAIL_FROM='ban@gmail.com'
```

- Gmail yêu cầu **mật khẩu ứng dụng** (bật xác minh 2 bước rồi tạo tại myaccount.google.com/apppasswords), không dùng mật khẩu đăng nhập.
- Email chỉ đến được hộp thư thật nếu **tài khoản người dùng trong hệ thống có địa chỉ email thật**. Tài khoản mẫu như `admin@soudan.local` hay `e2e-...@soudan.local` là địa chỉ giả nên không bao giờ nhận được thư.
- Lỗi gửi (sai mật khẩu, bị chặn...) được ghi log của backend (`Gửi email tới ... thất bại`), sự kiện được giữ lại và gửi lại khi khởi động lại backend.

## Trợ lý AI

Trợ lý AI gọi Claude qua API; nội dung người hỏi được phép xem (báo cáo, công việc, chat nhóm, ghi chú lịch) được gửi tới nhà cung cấp trong mỗi lượt hỏi.

```
# bash
export ANTHROPIC_API_KEY='<khoá API>'
# PowerShell
$env:ANTHROPIC_API_KEY='<khoá API>'
```

- Có thể đặt trong file `.env` (xem phần cấu hình mail ở trên). **Không** commit khoá.
- `AI_MODEL` đổi mô hình (mặc định `claude-sonnet-5-5`).
- `AI_PROVIDER=mock` để chạy thử giao diện không gọi ra ngoài.
- Chưa đặt khoá thì ứng dụng vẫn chạy; trang Trợ lý AI hiện thông báo chưa cấu hình.

### Chạy mô hình tại chỗ (dữ liệu không rời mạng nội bộ)

Dùng được Ollama, vLLM hoặc LM Studio (giao thức tương thích OpenAI). Ví dụ với Ollama:

```
# 1) Cài Ollama (ollama.com) rồi tải một mô hình, ví dụ:
ollama pull qwen2.5:7b
# 2) Đặt độ dài ngữ cảnh đủ lớn TRƯỚC khi chạy Ollama (mặc định chỉ 2048 token, prompt sẽ bị cắt âm thầm)
setx OLLAMA_CONTEXT_LENGTH 8192
# 3) Chạy backend
AI_PROVIDER=local AI_MODEL=qwen2.5:7b AI_BASE_URL=http://localhost:11434 AI_TIMEOUT=180s AI_MAX_CONTEXT_CHARS=8000
```

- Mô hình nhỏ trả lời chậm và kém hơn Claude, nhất là tiếng Việt và tiếng Nhật; giảm `AI_MAX_CONTEXT_CHARS` và tăng `AI_TIMEOUT`.
- Muốn mô hình chạy trên GPU thì cần đủ VRAM (7B lượng tử hoá 4-bit cần khoảng 5 GB; thiếu thì Ollama chạy một phần trên CPU, chậm hơn).

