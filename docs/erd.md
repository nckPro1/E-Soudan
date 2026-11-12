# ERD — Cơ sở dữ liệu

> **Stage 1 (auth) — đã chốt và có migration `V2__create_users.sql`.** Chỉ có `users` và `refresh_token`. Các bảng của stage sau thêm khi tới stage đó.
> Theo `stages/README.md`: nếu `stages/stage-N.md` khác ERD thì **ERD thắng**.
> Quy ước (`ARCHITECTURE.md` mục 5.1, 6): `snake_case`; khoá chính `BIGINT` identity; thời gian `timestamptz` (UTC); cột audit + `version` + `deleted_at` trên bảng nghiệp vụ.

## Sơ đồ

```mermaid
erDiagram
    users ||--o{ refresh_token : "có"
    users ||--o{ users : "created_by / updated_by"

    users {
        bigint id PK
        varchar email "duy nhất, không phân biệt hoa thường"
        varchar password_hash "BCrypt"
        varchar full_name
        varchar position "nhập tự do, có thể null"
        varchar system_role "ADMIN | MANAGER | USER"
        varchar status "ACTIVE | LOCKED"
        boolean must_change_password
        timestamptz created_at
        bigint created_by FK
        timestamptz updated_at
        bigint updated_by FK
        bigint version
        timestamptz deleted_at "xoá mềm"
    }
    refresh_token {
        bigint id PK
        bigint user_id FK
        varchar token_hash "SHA-256, duy nhất"
        timestamptz expires_at
        timestamptz revoked_at "null = còn hiệu lực"
        timestamptz created_at
    }
```

## `users`

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | BIGINT identity | PK | |
| `email` | VARCHAR(255) | NOT NULL | Duy nhất theo `lower(email)` (chỉ mục unique bên dưới) |
| `password_hash` | VARCHAR(100) | NOT NULL | BCrypt (60 ký tự), không bao giờ lưu mật khẩu thô |
| `full_name` | VARCHAR(100) | NOT NULL | |
| `position` | VARCHAR(100) | NULL | Vị trí, nhập tự do (ví dụ "Kế toán") |
| `system_role` | VARCHAR(20) | NOT NULL, CHECK IN (`ADMIN`,`MANAGER`,`USER`) | Mặc định `USER` |
| `status` | VARCHAR(20) | NOT NULL, CHECK IN (`ACTIVE`,`LOCKED`) | Mặc định `ACTIVE` |
| `must_change_password` | BOOLEAN | NOT NULL, mặc định `true` | |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL, mặc định `now()` | |
| `created_by` / `updated_by` | BIGINT | NULL, FK → `users(id)` | Null cho tài khoản Admin tạo lúc khởi động (chưa có người tạo) |
| `version` | BIGINT | NOT NULL, mặc định 0 | Khoá lạc quan (`@Version`) |
| `deleted_at` | TIMESTAMPTZ | NULL | Xoá mềm |

Chỉ mục: unique `lower(email)` **chỉ với bản ghi chưa xoá** (`WHERE deleted_at IS NULL`) — xem quyết định 1.

## `refresh_token`

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | BIGINT identity | PK | |
| `user_id` | BIGINT | NOT NULL, FK → `users(id)` | |
| `token_hash` | VARCHAR(64) | NOT NULL, UNIQUE | SHA-256 dạng hex; **không lưu token thô** |
| `expires_at` | TIMESTAMPTZ | NOT NULL | |
| `revoked_at` | TIMESTAMPTZ | NULL | Đặt khi đăng xuất / bị thu hồi |
| `created_at` | TIMESTAMPTZ | NOT NULL, mặc định `now()` | |

Chỉ mục: `user_id` (tìm token theo người dùng; thu hồi hết token khi khoá tài khoản).
Bảng này không cần `version`/xoá mềm (chỉ thêm và đánh dấu thu hồi).

## Dự kiến migration

`V2__create_users.sql` → `users`, `refresh_token`, các chỉ mục (một file duy nhất, quyết định 3).
Không sửa file migration sau khi đã chạy; muốn đổi thì thêm migration mới.

## Quyết định đã chốt (chủ dự án đồng ý với đề xuất)

1. **Email có thể dùng lại sau khi xoá mềm không?** Đề xuất: có (unique chỉ trên bản ghi chưa xoá). Nếu không: unique trên toàn bảng.
2. **Có `refresh_token` bảng riêng không?** Đề xuất: có, để đăng xuất và khoá tài khoản thu hồi được phiên (`stage-1.md` 1.3). Phương án khác: không lưu, chỉ dùng JWT hết hạn.
3. **Một hay hai file migration?** Đề xuất: một file `V2` cho cả hai bảng (cùng thuộc stage auth).
4. **Tên cột `position`**: PostgreSQL cho dùng (từ khoá không bị cấm), nhưng dễ nhầm với hàm `position()`. Đề xuất giữ `position` cho khớp `stage-1.md`; đổi sang `job_title` nếu bạn thích an toàn hơn.
5. **`created_by` có khoá ngoại về `users` không?** Đề xuất: có, nullable. Lưu ý khi xoá cứng user phải xử lý; vì dùng xoá mềm nên ổn.
