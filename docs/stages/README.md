# docs/stages — Hướng dẫn làm từng bước cho mỗi stage

> Mỗi file `stage-N.md` chia một stage của `IMPLEMENTATION_PLAN.md` thành **các bước nhỏ, theo thứ tự làm**.
> Đọc cùng `BUSINESS_OVERVIEW.md` (nghiệp vụ), `ARCHITECTURE.md` (kiến trúc), `TESTING_STRATEGY.md` (kiểm thử).
> Tên cột, giá trị trong các bước là **đề xuất**; bảng và cột chính thức chốt ở ERD (`docs/erd.md`). Nếu khác nhau, ERD thắng.

## Phạm vi hiện có

| File | Nội dung | Ghi chú |
|---|---|---|
| (Stage 0) | Dựng khung | Xem `docs/SETUP.md` |
| `stage-1.md` | Nền tảng và đăng nhập | |
| `stage-2.md` | Nhân sự và nhóm | |
| `stage-3.md` | Task | |
| `stage-4.md` | Báo cáo hằng ngày | |
| `stage-5.md` | Hoàn thiện MVP và deploy | |
| Stage 6–11 | Thông báo, chat, DM, mail, dashboard, production | **Chưa viết chi tiết.** Sẽ viết sau khi MVP chạy, vì phụ thuộc vào code thật. Trước đó dùng mục tương ứng trong `IMPLEMENTATION_PLAN.md`. |

## Ký hiệu người làm

- **[BẠN]**: phần lõi do chủ dự án tự viết (entity, quy tắc nghiệp vụ, service, phân quyền). AI chỉ gợi ý hướng, giải thích, chỉ ra lỗi khi được hỏi.
- **[AI]**: phần phụ do coder agent viết (DTO, mapper, controller mỏng, cấu hình, CRUD frontend). Chủ dự án đọc và hiểu trước khi commit.
- **[CẢ HAI]**: AI phác nháp, chủ dự án chỉnh và chịu trách nhiệm hiểu từng dòng.
- **[TESTER]**, **[REVIEWER]**: các agent theo `TESTING_STRATEGY.md`.

## Vòng chuẩn của một lát dọc (áp dụng cho mọi chức năng)

1. **Test case:** soạn `docs/test-cases/stage-N.md`, chủ dự án duyệt.
2. **Migration:** thêm file Flyway mới.
3. **Domain:** entity, enum, quy tắc trạng thái.
4. **Repository:** truy vấn cần thiết.
5. **Service:** nghiệp vụ, `@Transactional`, kiểm tra quyền.
6. **Web:** DTO record, mapper, controller, mô tả OpenAPI.
7. **Test backend:** unit, integration, phân quyền, kiểm tra cấu trúc module.
8. **Frontend:** `api.ts` → store (nếu cần) → view → route.
9. **Cổng:** reviewer → tester thử phá → `verify-stage.sh` → chủ dự án chạy thủ công → review PR.

Trong mỗi file stage, các bước được đánh số `N.x` và gắn với vòng chuẩn này.

## Quy tắc chung

- Mỗi bước có **"Xong bước khi…"**: kiểm tra được ngay (chạy test, gọi API, nhìn giao diện) trước khi sang bước sau.
- Commit sau mỗi bước hoặc nhóm bước nhỏ, theo quy ước trong `CLAUDE.md`.
- Gặp điểm **[Mặc định]** hoặc chỗ nghiệp vụ chưa rõ: dừng và hỏi chủ dự án.
- Không làm trước bước của stage sau.
