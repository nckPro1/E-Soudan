# Review — Stage N: <tên stage>

> Sao chép file này thành `stage-N.md`. Reviewer và tester ghi vào đây; coder đổi trạng thái lỗi.
> Định dạng mỗi lỗi:
> `- [BLOCKER|MAJOR|MINOR][OPEN|FIXED|WONTFIX] <file>:<dòng> — <mô tả> — Vi phạm: <tài liệu + mục> — Gợi ý/Tái hiện: <...>`
> Cổng chỉ qua khi **không còn** `[BLOCKER][OPEN]` hoặc `[MAJOR][OPEN]`.

- Phạm vi diff: `<nhánh/tag gốc>..<HEAD>`
- Vòng review: ☐ 1  ☐ 2
- Ngày: ____

## Reviewer findings

<!-- ví dụ:
- [MAJOR][OPEN] task/web/TaskController.java:42 — Controller gọi thẳng TaskRepository — Vi phạm: ARCHITECTURE.md 4.2 (1) — Gợi ý: đi qua TaskService
-->

## Tester findings

<!-- ví dụ:
- [BLOCKER][OPEN] task/service/TaskService.java:88 — Member nhóm B duyệt được task nhóm A — Vi phạm: BUSINESS_OVERVIEW.md 4 (Leader chỉ nhóm mình) — Tái hiện: should_return403_when_leaderOfOtherTeamApproves
-->

## Đánh giá chất lượng test

- Có test cho nhánh bị từ chối quyền? ☐ có ☐ thiếu
- Có test bị tắt hoặc bị làm yếu? ☐ không ☐ có (ghi rõ)
- Số test so với baseline: ____ → ____

## Bất đồng cần quyết (chủ dự án)

<!-- Nếu coder và reviewer/tester không thống nhất sau 2 vòng, ghi cả hai quan điểm ở đây. -->

| Vấn đề | Quan điểm A (người đề xuất) | Quan điểm B | Chủ dự án quyết |
|---|---|---|---|
| | | | |

## Tóm tắt cho chủ dự án (3–5 dòng)

- Làm tốt:
- Rủi ro lớn nhất:
- Nên tự kiểm tra tay:
