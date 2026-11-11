# Test cases — Stage N: <tên stage>

> Sao chép file này thành `stage-N.md`. **Chủ dự án duyệt file này trước khi viết code.**
> Nguồn: mục "Xong khi…" của stage trong `IMPLEMENTATION_PLAN.md` và các quy tắc trong `BUSINESS_OVERVIEW.md`.

- Trạng thái duyệt: ☐ chưa duyệt / ☐ đã duyệt (ngày: ____ )
- Loại: **AUTO** = có test tự động; **MANUAL** = chủ dự án tự chạy bằng tay.
- Trạng thái chạy: ☐ chưa chạy · ✔ đạt · ✘ không đạt (ghi ghi chú).

## 1. Luồng đúng (happy path)

| ID | Loại | Vai | Điều kiện đầu | Các bước | Kết quả mong đợi | Test (tên/đường dẫn) | Trạng thái |
|---|---|---|---|---|---|---|---|
| S{N}-001 | AUTO | LEADER | | | | | ☐ |
| S{N}-002 | MANUAL | MEMBER | | | | — | ☐ |

## 2. Phân quyền (mỗi dòng trong bảng quyền có một case được phép và một case bị từ chối)

| ID | Loại | Vai | Hành động | Kết quả mong đợi (cho phép / 403) | Test | Trạng thái |
|---|---|---|---|---|---|---|
| S{N}-101 | AUTO | MEMBER | | 403 | | ☐ |

## 3. Dữ liệu sai và trường hợp biên

| ID | Loại | Mô tả | Kết quả mong đợi (ví dụ 422 / 409 / chặn) | Test | Trạng thái |
|---|---|---|---|---|---|
| S{N}-201 | AUTO | | | | ☐ |

## 4. Chuyển trạng thái (nếu stage có máy trạng thái)

| ID | Loại | Từ | Sang | Ai thực hiện | Kết quả mong đợi (hợp lệ / bị từ chối) | Test | Trạng thái |
|---|---|---|---|---|---|---|---|
| S{N}-301 | AUTO | | | | | | ☐ |

## 5. Kiểm tra thủ công giao diện và trải nghiệm

| ID | Loại | Việc cần xem | Cách làm | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| S{N}-401 | MANUAL | Màu trạng thái, thông báo lỗi, trạng thái trống/đang tải | | | ☐ |

## 6. Hồi quy

- ☐ Toàn bộ test của các stage trước vẫn đạt (`scripts/verify-stage.sh N`).

## 7. Ghi chú / câu hỏi cho chủ dự án

-
