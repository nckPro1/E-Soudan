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
| Thư viện UI | **PrimeVue** + `@primeuix/themes` (Aura, chỉnh màu chàm) + `primeicons`; cấu hình ở `frontend/src/app/plugins/primevue.ts` |

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
