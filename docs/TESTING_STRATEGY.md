# TESTING_STRATEGY — Chiến lược kiểm thử và cổng chuyển stage

> Đọc cùng `BUSINESS_OVERVIEW.md`, `ARCHITECTURE.md`, `IMPLEMENTATION_PLAN.md`.
> Tài liệu này quy định **cách kiểm tra một stage trước khi sang stage kế tiếp**: loại test nào, ai làm, khi nào được qua cổng.
> Các mục đánh dấu **[Mặc định]** là lựa chọn tạm, chưa được chủ dự án xác nhận. Nếu cần đổi, hỏi lại.

---

## 1. Nguyên tắc

1. **Cổng chuyển stage do script quyết định bằng kết quả pass/fail**, không do AI tự tuyên bố "xong rồi".
2. **Người giữ hai cổng chính** (duyệt test case, và review/chạy thủ công cuối stage). AI kiểm tra chéo nhau ở giữa; không có vòng lặp tự chạy đến cùng không người giám sát.
3. **Test xuất phát từ nghiệp vụ, không từ code.** Test viết theo cách code đang chạy sẽ cùng mù một chỗ với code.
4. **Mỗi vai AI chạy trong context riêng** và có quyền hạn riêng (coder / tester / reviewer).
5. **Hồi quy:** mỗi lần qua cổng, chạy lại toàn bộ test của các stage trước.
6. Mục tiêu là code đúng và chủ dự án **hiểu được code**; không chạy theo con số coverage.

## 2. Các loại test

| Loại | Phạm vi | Công cụ | Ai thực hiện |
|---|---|---|---|
| Unit (backend) | Service, domain (máy trạng thái task, quy tắc nghiệp vụ) | JUnit 5, Mockito | Tự động |
| Integration (backend) | Repository, controller + Security + DB thật | `@SpringBootTest`/`@DataJpaTest`, Testcontainers (PostgreSQL), MockMvc | Tự động |
| Phân quyền | Bảng quyền trong `BUSINESS_OVERVIEW.md` mục 4, viết dạng bảng dữ liệu (`@ParameterizedTest`) | JUnit 5 | Tự động |
| Cấu trúc module | Ranh giới module, không vòng phụ thuộc | Spring Modulith `verify()` | Tự động |
| Unit (frontend) | Composable (`useList`, `useForm`, `usePermission`), store | Vitest | Tự động |
| Component (frontend) | Component quan trọng | Vitest + Vue Test Utils | Tự động |
| E2E | Luồng chính trên trình duyệt thật | Playwright | Tự động (vài luồng chính) |
| Thủ công | Giao diện, màu sắc, trải nghiệm, chat realtime giữa hai trình duyệt, trường hợp lạ | Checklist | **Chủ dự án** |
| Chất lượng test (tuỳ chọn) | Test có đủ mạnh không | PIT (mutation testing) | Tự động **[Mặc định: để sau stage 5]** |

Quy ước đặt tên test: `should_<kết quả mong đợi>_when_<điều kiện>`; cấu trúc given / when / then.

Vị trí: `backend/src/test/java/...` (cùng package với code), `frontend/src/**/*.test.ts`, `e2e/` (Playwright).

## 3. Ba vai AI

| Vai | File | Quyền | Việc |
|---|---|---|---|
| Coder | `.claude/agents/coder.md` | Sửa code production, **không** sửa/xoá test đã duyệt | Viết code theo stage cho test pass |
| Tester | `.claude/agents/tester.md` | Chỉ sửa file test và `docs/test-cases/` | Viết test từ nghiệp vụ trước khi code; sau đó thử phá |
| Reviewer | `.claude/agents/reviewer.md` | Chỉ đọc code; chỉ ghi `docs/reviews/stage-N.md` | Soi diff và test theo tài liệu, xuất danh sách lỗi |

Để kiểm tra chéo có giá trị:
- Tester **viết test trước khi đọc phần hiện thực**; chỉ dựa vào tài liệu nghiệp vụ và test case đã duyệt.
- Reviewer chạy trong context mới, không thấy lập luận của coder. **[Mặc định]** dùng cùng model (`model: inherit`); nếu muốn kiểm tra chéo mạnh hơn, đổi `model` trong `reviewer.md` sang model khác.

## 4. Luồng một stage

```
1. Soạn test case  ──► [CỔNG NGƯỜI 1: bạn duyệt]
2. Tester viết test (đỏ)
3. Coder làm cho test xanh
4. Reviewer review  ──► Coder sửa  (tối đa 2 vòng)
5. Tester "thử phá"  ──► Coder sửa lỗi tìm được
6. scripts/verify-stage.sh  (cổng tự động)
7. Bạn: đọc báo cáo review + chạy checklist thủ công + review PR  ──► [CỔNG NGƯỜI 2]
8. Merge, gắn tag stage-N-done, sang stage kế
```

### Bước 1 — Soạn test case
- AI chuyển các dòng "Xong khi…" của stage (trong `IMPLEMENTATION_PLAN.md`) và quy tắc nghiệp vụ liên quan thành `docs/test-cases/stage-N.md` (dùng mẫu `docs/test-cases/_template.md`).
- Mỗi test case có ID, loại (AUTO hoặc MANUAL), các bước, kết quả mong đợi, trạng thái.
- Phải có test case cho: luồng đúng, **trường hợp bị từ chối quyền (403)**, dữ liệu sai (422), xung đột phiên bản (409), và dữ liệu biên.
- **Chủ dự án duyệt file này trước khi viết bất kỳ dòng code nào.**

### Bước 2 — Tester viết test
- Chỉ dựa vào tài liệu nghiệp vụ và file test case. Chạy test, xác nhận **đỏ vì thiếu tính năng** (không phải đỏ vì lỗi cấu hình).

### Bước 3 — Coder
- Làm cho test xanh. **Không được** xoá, tắt (`@Disabled`, `.skip`), hoặc làm yếu test đã duyệt. Nếu cho rằng test sai, ghi vào báo cáo và dừng để hỏi chủ dự án.

### Bước 4 — Reviewer
- So diff với: `ARCHITECTURE.md` (mục 4.2 quy tắc phụ thuộc, mục 7 bảo mật), bảng quyền, "Xong khi…", Definition of Done.
- Đánh giá luôn chất lượng của chính các test (test có thật sự kiểm tra điều quan trọng không).
- Ghi lỗi theo định dạng ở mục 5. Coder sửa; tối đa **2 vòng**.

### Bước 5 — Tester thử phá
- Dữ liệu biên, sai quyền, gửi đúp, thao tác đồng thời (xung đột phiên bản), đổi trạng thái sai thứ tự, truy cập chéo nhóm. Bug tìm được ghi vào báo cáo; coder sửa; bổ sung test hồi quy.

### Bước 6 — Cổng tự động
```
scripts/verify-stage.sh <N> [--e2e]
```
Xem mục 6. Phải ra mã thoát 0.

### Bước 7 — Cổng người 2
- Đọc `docs/reviews/stage-N.md`.
- Chạy các test case loại MANUAL, cập nhật trạng thái trong `docs/test-cases/stage-N.md`.
- Review PR; tự giải thích được các phần cốt lõi (mục tiêu là luyện lại Java).

### Bước 8 — Đóng stage
- Cập nhật bảng tiến độ trong `IMPLEMENTATION_PLAN.md`, chạy `scripts/verify-stage.sh <N> --update-baseline`, merge, gắn tag `stage-N-done`.

## 5. Định dạng báo cáo review

File `docs/reviews/stage-N.md` (mẫu `docs/reviews/_template.md`). Mỗi lỗi một dòng:

```
- [BLOCKER][OPEN] <file>:<dòng> — <mô tả ngắn> — Vi phạm: <tài liệu + mục> — Gợi ý: <cách sửa>
```

- Mức độ: `BLOCKER` (sai nghiệp vụ, hở bảo mật, hỏng dữ liệu), `MAJOR` (vi phạm kiến trúc, thiếu test quan trọng), `MINOR` (cải thiện, ghi lại không bắt buộc sửa).
- Trạng thái: `OPEN` → `FIXED` (kèm commit) hoặc `WONTFIX` (kèm lý do, chủ dự án đồng ý).
- Mỗi lỗi phải **dẫn chiếu quy tắc bị vi phạm**. Không nhận xét mơ hồ về phong cách.
- **Cổng chỉ qua khi không còn dòng `[BLOCKER][OPEN]` hoặc `[MAJOR][OPEN]`.**

### Khi các AI bất đồng
Nếu coder và reviewer (hoặc tester) không thống nhất sau 2 vòng: **dừng**, ghi cả hai quan điểm vào mục "Bất đồng cần quyết" của báo cáo, hỏi chủ dự án. Không tự cãi nhau tiếp.

## 6. Cổng tự động — `scripts/verify-stage.sh`

Script kiểm tra theo thứ tự:
1. Có `docs/test-cases/stage-N.md`; có `docs/reviews/stage-N.md` và không còn lỗi `BLOCKER`/`MAJOR` ở trạng thái `OPEN`.
2. Không có test bị tắt hoặc bị khoá (`@Disabled`, `@Ignore`, `.skip`, `.only`, `xit`…).
3. Backend: `./gradlew check` (unit, integration, phân quyền, kiểm tra cấu trúc module).
4. Frontend: lint, kiểm tra kiểu (typecheck), Vitest.
5. (Tuỳ chọn `--e2e`) Playwright.
6. **Số test không giảm** so với `.test-baseline` (chống xoá test để cho pass).

Mã thoát 0 là qua cổng. Chạy cục bộ trước khi mở PR; chạy lại trên CI cho mỗi PR (GitHub Actions) làm cổng độc lập với máy của agent.

## 7. Hook cho Claude Code (tuỳ chọn)

Có thể cấu hình hook `Stop` để coder không kết thúc khi test còn đỏ:
- Hook chạy **bộ test nhanh** (unit), không chạy bộ đầy đủ (hook chạy đồng bộ, bộ chậm sẽ làm treo).
- Hook phải kiểm tra cờ `stop_hook_active` để tránh vòng lặp vô hạn.
- Bộ đầy đủ (`verify-stage.sh`) chạy thủ công ở bước 6 và trên CI.
- Chi tiết cấu hình thay đổi theo phiên bản Claude Code; kiểm tra tài liệu chính thức khi cài.

## 8. Dữ liệu và môi trường test

- Backend integration test dùng PostgreSQL thật bằng Testcontainers; không dùng H2 để thay thế (khác hành vi).
- Mỗi test tự chuẩn bị dữ liệu của nó (builder/factory), không phụ thuộc thứ tự chạy.
- Có sẵn bộ tài khoản mẫu cho bốn vai trò (ADMIN, MANAGER, LEADER, MEMBER) dùng chung cho test phân quyền và E2E.
- Đồng hồ: dùng `Clock` được inject để test các quy tắc theo ngày (báo cáo mỗi ngày, task quá hạn).
- Mail (stage 9): dùng MailHog hoặc mock; không gửi mail thật khi test.

## 9. Chỉ số tham khảo (không phải mục tiêu)

- Độ phủ dòng của `service` và `domain` nên ≥ 70% (JaCoCo) như chỉ báo, không phải điều kiện bắt buộc.
- Mỗi dòng trong bảng quyền có ít nhất một test cho nhánh được phép và một test cho nhánh bị từ chối.
- Mỗi chuyển trạng thái hợp lệ của task có test; mỗi chuyển trạng thái không hợp lệ có test bị từ chối.

## 10. Quy tắc cho Claude Code

- Tuân theo luồng ở mục 4; không bỏ bước, không tự quyết thay hai cổng người.
- Coder: không xoá/tắt/làm yếu test đã duyệt; thấy test sai thì dừng và hỏi.
- Tester: không đọc phần hiện thực khi viết test lần đầu; chỉ sửa file test và test case.
- Reviewer: chỉ đọc và ghi báo cáo; mỗi lỗi phải dẫn chiếu quy tắc.
- Không nới lỏng `verify-stage.sh` để cho pass. Muốn đổi cổng thì hỏi chủ dự án.
- Khi bất đồng giữa các agent, dừng và hỏi (mục 5).
- Giải thích ngắn gọn khái niệm Java/Spring/test mới xuất hiện trong stage, để chủ dự án học được.
