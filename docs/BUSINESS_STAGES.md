# BUSINESS_STAGES — Các stage nhìn từ nghiệp vụ

> Bổ sung cho `docs/stages/stage-N.md` (các file đó nói **làm thế nào**: migration, service, controller…).
> Tài liệu này nói **hệ thống phải làm được gì cho người dùng**, ai được làm gì, và khi nào một stage được coi là xong **về mặt nghiệp vụ**.
>
> **Nguồn:** `BUSINESS_OVERVIEW.md` chưa có trong repo, nên nội dung dưới đây được suy ra từ `docs/stages/stage-1..5.md` và `TESTING_STRATEGY.md`.
> Khi `BUSINESS_OVERVIEW.md` ra đời, nó thắng; sửa file này cho khớp.
> Các mục đánh dấu **[Cần xác nhận]** là điều tôi suy ra hoặc là lựa chọn tạm, chưa được chủ dự án chốt.
> Stage 6–11 chưa viết chi tiết (xem `docs/stages/README.md`), nên chưa có ở đây.

---

## 0. Bức tranh chung

Ứng dụng là công cụ nội bộ của công ty để **giao việc, báo cáo công việc hằng ngày và phản hồi**, theo phong cách tối giản kiểu Nhật (con dấu 報 – "báo cáo" – làm logo).

### Hai lớp vai trò

| Lớp | Giá trị | Ý nghĩa |
|---|---|---|
| **Vai trò hệ thống** (`system_role`) | ADMIN, MANAGER, USER | Gắn với tài khoản, áp dụng toàn hệ thống |
| **Vai trò trong nhóm** (`team_role`) | LEADER, MEMBER, OBSERVER | Gắn với từng nhóm; một người có thể là Leader nhóm A và Member nhóm B |

Bốn vai trò dùng khi nói chuyện nghiệp vụ và khi demo/test:

| Vai trò nghiệp vụ | Thực chất là | Việc chính |
|---|---|---|
| **Admin** | `system_role = ADMIN` | Tạo tài khoản, dựng nhóm, quản lý nhân sự. Không tham gia nhóm **[Cần xác nhận]** |
| **Manager** | `system_role = MANAGER` | Xem nhân sự, theo dõi báo cáo của Leader, nhận xét; có thể làm OBSERVER của nhóm |
| **Leader** | USER có `team_role = LEADER` | Giao việc, duyệt việc, xem báo cáo Member, nộp báo cáo cho Manager |
| **Member** | USER có `team_role = MEMBER` | Nhận việc, làm, nộp file, nộp báo cáo hằng ngày |

### Chuỗi giá trị MVP

```
Admin tạo tài khoản ──► Admin dựng nhóm (Leader + Member + Observer)
        ──► Leader giao task ──► Member làm, nộp file ──► Leader duyệt / trả lại
        ──► Member báo cáo mỗi ngày ──► Leader nhận xét ──► Leader báo cáo cho Manager
```

Mỗi stage mở một đoạn của chuỗi này.

---

## Stage 1 — Xác thực và quản lý tài khoản

**Câu hỏi nghiệp vụ:** *Ai được vào hệ thống, và ai được cấp quyền cho họ?*

### Mục tiêu
Chỉ người được công ty cấp tài khoản mới đăng nhập được. Admin kiểm soát vòng đời tài khoản.

### Tác nhân
Admin (quản lý tài khoản), mọi người dùng (đăng nhập, đổi mật khẩu).

### Chức năng nghiệp vụ

| # | Chức năng | Ai làm | Quy tắc |
|---|---|---|---|
| A1 | Đăng nhập bằng email và mật khẩu | Mọi người | Chỉ tài khoản `ACTIVE` mới đăng nhập được |
| A2 | Tạo tài khoản | Admin | Không có tự đăng ký. Email duy nhất, không phân biệt hoa thường |
| A3 | Đổi mật khẩu bắt buộc | Người dùng mới | Tài khoản mới (và Admin đầu tiên) phải đổi mật khẩu tạm ở lần đăng nhập đầu |
| A4 | Đổi mật khẩu | Chính người dùng | Phải nhập đúng mật khẩu cũ |
| A5 | Sửa hồ sơ | Admin | Gồm họ tên và **vị trí** (nhập tự do, ví dụ "Kế toán") |
| A6 | Gán vai trò MANAGER | Admin | Mặc định tài khoản mới là USER |
| A7 | Khoá / mở khoá tài khoản | Admin | Người bị khoá không đăng nhập được |
| A8 | Phiên làm việc | Hệ thống | Hết hạn thì phải đăng nhập lại; không có token → từ chối |
| A9 | Đăng xuất | Mọi người | Thu hồi phiên |

### Quy tắc bảo vệ
- **Admin cuối cùng** không thể tự khoá mình hoặc tự hạ quyền (hệ thống không bao giờ được rơi vào tình trạng không còn Admin).
- Admin đầu tiên được tạo từ cấu hình khi khởi động, không đặt mật khẩu cố định trong code; chỉ tạo một lần.
- Backend là nơi quyết định quyền; ẩn nút ở giao diện chỉ là tiện lợi.

### Bảng quyền (Stage 1)

| Hành động | Admin | Manager | User | Chưa đăng nhập |
|---|---|---|---|---|
| Đăng nhập | ✔ | ✔ | ✔ | ✔ |
| Xem/đổi mật khẩu của mình | ✔ | ✔ | ✔ | 401 |
| Xem danh sách, tạo, sửa, khoá tài khoản | ✔ | 403 | 403 | 401 |
| Gán vai trò MANAGER | ✔ | 403 | 403 | 401 |

### Trải nghiệm đăng nhập (từ thiết kế Stitch)
Có đủ 5 trạng thái màn hình đăng nhập, mỗi trạng thái ứng với một tình huống nghiệp vụ:

| Màn hình | Khi nào |
|---|---|
| Default | Mới vào |
| Loading | Đang xác thực |
| Error | Sai email hoặc mật khẩu |
| Account Locked | Tài khoản bị Admin khoá |
| Session Expired | Phiên hết hạn |

### Tình huống biên cần xử lý đúng
- Đăng nhập sai mật khẩu nhiều lần: **[Cần xác nhận]** có tự khoá sau N lần không? Hiện tài liệu stage chỉ có khoá do Admin.
- Tài khoản bị khoá **giữa phiên**: lần gọi kế tiếp phải bị từ chối.
- Tạo tài khoản với email đã tồn tại (kể cả khác hoa/thường) → báo trùng.

### Xong về mặt nghiệp vụ khi
Admin đăng nhập, tạo được tài khoản USER và MANAGER; người mới phải đổi mật khẩu tạm; USER đăng nhập được nhưng không vào được chức năng Admin (403); không token → 401; tài khoản bị khoá không đăng nhập được.

---

## Stage 2 — Nhân sự và nhóm

**Câu hỏi nghiệp vụ:** *Công ty được tổ chức thành những nhóm nào, ai giữ vai gì trong nhóm?*

### Mục tiêu
Admin dựng được cơ cấu công ty; Manager nhìn được toàn bộ; mỗi người thấy các nhóm mình tham gia.

### Chức năng nghiệp vụ

| # | Chức năng | Ai làm | Quy tắc |
|---|---|---|---|
| T1 | Tạo nhóm, chọn Leader | Admin | Nhóm phải có **ít nhất một Leader** |
| T2 | Thêm / bớt thành viên, đổi vai trong nhóm | Admin | Không thêm trùng một người hai lần |
| T3 | Thêm Manager làm **OBSERVER** | Admin | OBSERVER phải là MANAGER |
| T4 | Lưu trữ nhóm | Admin | Nhóm đã lưu trữ **không sửa được** |
| T5 | Xem chi tiết nhóm | Thành viên, Leader, Observer, Admin | |
| T6 | Tìm/lọc nhân sự theo tên, vị trí, trạng thái | Admin, Manager | Manager chỉ xem |

### Quy tắc nghiệp vụ
1. Một nhóm luôn có ít nhất một Leader; **không được xoá hoặc hạ vai Leader cuối cùng**.
2. Một người có thể là **Leader của nhiều nhóm**, và đồng thời là Member của nhóm khác.
3. Leader và Member phải là tài khoản `USER` đang `ACTIVE`.
4. OBSERVER phải có `system_role = MANAGER`.
5. **Admin không tham gia nhóm** **[Cần xác nhận]**.
6. Tên nhóm không trùng giữa các nhóm đang hoạt động.
7. Hai người cùng sửa một nhóm một lúc: người sau nhận báo xung đột (409), không ghi đè lặng lẽ.

### Bảng quyền (Stage 2)

| Hành động | Admin | Manager | Leader nhóm đó | Member nhóm đó | Người ngoài nhóm |
|---|---|---|---|---|---|
| Tạo/sửa/lưu trữ nhóm, quản lý thành viên | ✔ | 403 | 403 | 403 | 403 |
| Xem chi tiết nhóm | ✔ | ✔ nếu là Observer | ✔ | ✔ | 403 |
| Xem danh sách nhân sự | ✔ | ✔ (chỉ xem) | thông tin tối thiểu | thông tin tối thiểu | — |
| Sửa nhân sự | ✔ | 403 | 403 | 403 | 403 |

> Leader nhóm A **không** sửa được nhóm B (và Leader cũng không tự sửa được nhóm của mình; việc quản lý nhóm thuộc Admin ở stage này).

### Tình huống biên
- Thêm người đã có trong nhóm → bị chặn.
- Người bị khoá (Stage 1) nhưng vẫn là thành viên nhóm: hiển thị thế nào? **[Cần xác nhận]**
- Nhóm đã lưu trữ có còn hiện trong danh sách không, task/báo cáo cũ có còn xem được không? **[Cần xác nhận]** (Stage 3 và 4 cũng nhắc tới nhóm lưu trữ).

### Xong về mặt nghiệp vụ khi
Admin tạo nhóm đầy đủ (Leader, Member, Observer); Manager xem nhân sự nhưng sửa thì bị 403; một người là Leader nhóm A và Member nhóm B hoạt động đúng; nhóm không bao giờ mất Leader cuối.

---

## Stage 3 — Giao việc (Task)

**Câu hỏi nghiệp vụ:** *Việc được giao, thực hiện, nộp và nghiệm thu như thế nào?*

### Mục tiêu
Một vòng đời task trọn vẹn: Leader giao → Member làm → Member nộp → Leader duyệt hoặc trả lại, có lịch sử đầy đủ.

### Vòng đời task

```
ASSIGNED ──(Member bắt đầu)──► IN_PROGRESS ──(Member nộp chờ duyệt)──► PENDING_REVIEW (cam)
                                    ▲                                        │
                                    │                  ┌─(Leader duyệt)──────┤
                                    └──(Leader trả lại, kèm lý do)           ▼
                                                                          DONE (xanh)
```

| Chuyển trạng thái | Ai làm | Điều kiện |
|---|---|---|
| ASSIGNED → IN_PROGRESS | Người phụ trách | |
| IN_PROGRESS → PENDING_REVIEW | Người phụ trách | Nếu task `require_file` thì phải có ít nhất một file đã nộp |
| PENDING_REVIEW → DONE | **Leader của nhóm đó** | |
| PENDING_REVIEW → trả lại → IN_PROGRESS | Leader của nhóm đó | Bắt buộc nhập **lý do**; lý do được lưu |

Mọi chuyển trạng thái khác (ví dụ duyệt khi chưa chờ duyệt, nhảy cóc) đều bị từ chối.

### Chức năng nghiệp vụ

| # | Chức năng | Ai làm | Quy tắc |
|---|---|---|---|
| K1 | Giao task | Leader | Người phụ trách phải là **Member của chính nhóm đó**; nhóm đang hoạt động. Có tiêu đề, mô tả, hạn chót, ưu tiên, cờ "yêu cầu nộp file" |
| K2 | Sửa task | Leader | Chỉ khi chưa `DONE` |
| K3 | Bắt đầu / nộp chờ duyệt | Người phụ trách | Theo bảng trên |
| K4 | Nộp file kết quả | Người phụ trách | Khi task đang `IN_PROGRESS`; có thể nộp lại |
| K5 | Tải file đã nộp | Người phụ trách, Leader nhóm | Người khác (kể cả Member khác, Observer) **không** tải được **[Cần xác nhận về Observer]** |
| K6 | Duyệt / trả lại | Leader nhóm | Member không duyệt được; Leader nhóm khác không duyệt được |
| K7 | Xem lịch sử | Người có quyền xem task | Dòng thời gian: ai đổi từ trạng thái nào sang trạng thái nào, lúc nào, ghi chú |
| K8 | "Việc của tôi" | Member | Lọc theo trạng thái, hạn chót |

### Quy tắc file
- Chỉ nhận PDF, Word, Excel, ảnh, zip; dung lượng tối đa 10 MB **[mặc định theo stage doc]**.
- File sai loại hoặc quá lớn bị từ chối, nói rõ lý do.

### Bảng quyền (Stage 3)

| Hành động | Leader nhóm đó | Member được giao | Member khác trong nhóm | Observer | Leader nhóm khác |
|---|---|---|---|---|---|
| Giao / sửa task | ✔ | 403 | 403 | 403 | 403 |
| Bắt đầu, nộp file, nộp chờ duyệt | — | ✔ | 403 | 403 | 403 |
| Duyệt / trả lại | ✔ | 403 | 403 | 403 | 403 |
| Tải file đã nộp | ✔ | ✔ | 403 | 403 | 403 |
| Xem task | ✔ | ✔ | ✔ **[Cần xác nhận]** | ✔ **[Cần xác nhận]** | 403 |

### Tình huống biên
- Hai người thao tác task cùng lúc (Leader duyệt khi Member vừa nộp lại) → người đến sau nhận 409 và giao diện tải lại.
- Giao task cho người không thuộc nhóm → chặn.
- Gọi trực tiếp API với `status` bất kỳ để "nhảy" trạng thái → phải bị từ chối.
- Hạn chót trong quá khứ khi tạo task? **[Cần xác nhận]**
- Task quá hạn có hiển thị khác không (màu "attention" của thiết kế)? **[Cần xác nhận]**

### Màu trạng thái (theo thiết kế)
Chờ xử lý: xám · Đang làm: chàm · **Chờ duyệt: cam** · **Hoàn thành: xanh matcha** · Quá hạn/cảnh báo: đỏ son.
> Thiết kế hiện chỉ có màn đăng nhập; màu cam của "chờ duyệt" chưa có trong hệ thống màu Stitch (bảng có chàm, đỏ son, matcha). **[Cần xác nhận]**

### Xong về mặt nghiệp vụ khi
Chạy trọn luồng giao – làm – nộp – duyệt hoặc trả lại có lý do; task yêu cầu file không chuyển sang chờ duyệt nếu thiếu file; Member không duyệt được; mọi lần đổi trạng thái đều có lịch sử.

---

## Stage 4 — Báo cáo hằng ngày và nhận xét

> **Cập nhật 13/11/2026 — Stage 4 đổi mô hình (chủ dự án chốt):** không còn chuỗi Member → Leader → Manager.
> Manager tạo **cộng đồng** (ví dụ "Phòng phát triển số 2") và thêm Leader/Member vào; ai được thêm mới thấy trang báo cáo.
> Mọi thành viên trong cộng đồng thấy báo cáo của nhau như feed của Teams; Leader không có quyền đặc biệt;
> Manager (chủ cộng đồng) đọc, nhận xét, thấy ai chưa nộp và quản lý thành viên. Admin không tham gia.
> Nội dung bên dưới là bản cũ, chỉ còn tham khảo (bảng quyền xem báo cáo, Observer, vai Leader đã bị thay thế).

**Câu hỏi nghiệp vụ:** *Mỗi người báo cáo cho ai, và ai được đọc báo cáo của ai?*

### Mục tiêu
Vòng báo cáo theo cấp: **Member → Leader → Manager**, kèm nhận xét ngược lại.

### Chuỗi báo cáo

```
Member ──báo cáo──► Leader của nhóm        Leader ──báo cáo──► Manager
       ◄──nhận xét──                              ◄──nhận xét──
```

### Chức năng nghiệp vụ

| # | Chức năng | Ai làm | Quy tắc |
|---|---|---|---|
| R1 | Nộp báo cáo hôm nay | Leader hoặc Member của nhóm | Nội dung: việc đã làm, kế hoạch, vướng mắc (+ task liên quan). **Observer không nộp** |
| R2 | Mỗi người, mỗi nhóm, mỗi ngày một báo cáo | Hệ thống | Nộp lần hai trong ngày → **cập nhật** báo cáo cũ **[Cần xác nhận]** |
| R3 | Sửa báo cáo | Tác giả | Chỉ trong ngày của báo cáo **[Cần xác nhận]** |
| R4 | Xem báo cáo của mình | Mọi tác giả | Theo ngày |
| R5 | Xem báo cáo cả nhóm theo ngày | Leader | Kèm danh sách **người chưa nộp** |
| R6 | Xem báo cáo của các Leader | Manager | Lọc theo nhóm, ngày |
| R7 | Nhận xét báo cáo | Leader (báo cáo Member nhóm mình); Manager (báo cáo Leader) | Tác giả thấy nhận xét. Chỉ thêm, chưa sửa/xoá **[Cần xác nhận]** |

### Quy tắc "ngày"
"Ngày" theo múi giờ cấu hình, mặc định `Asia/Ho_Chi_Minh` **[Cần xác nhận]**. Báo cáo qua nửa đêm thuộc ngày nào do giờ này quyết định.

### Bảng quyền xem báo cáo (Stage 4 — phần dễ sai nhất)

| Người xem ↓ / Báo cáo của → | Chính mình | Member cùng nhóm | Member nhóm mình lead | Leader | Member nhóm khác |
|---|---|---|---|---|---|
| **Member** | ✔ | 403 | — | 403 | 403 |
| **Leader** | ✔ | — | ✔ | — | 403 |
| **Manager** | — | **403** | **403** | ✔ (Leader của nhóm) | **403** |
| **Observer** | — | **403** | **403** | ✔ nếu là Manager xem Leader | **403** |
| **Admin** | **[Cần xác nhận]** | | | | |

Điểm cần nhớ: **Manager xem báo cáo Leader, không xem báo cáo Member**; Observer không xem báo cáo Member.

### Tình huống biên
- Đoán id báo cáo của người khác rồi gọi API → 403, không lộ nội dung.
- Nộp báo cáo vào nhóm mình không thuộc → chặn.
- Nộp báo cáo cho ngày khác (quá khứ/tương lai) → chặn.
- Báo cáo của nhóm đã lưu trữ: xem được, nhưng không nộp thêm **[Cần xác nhận]**.
- Một người là Leader nhóm A, Member nhóm B: báo cáo ở A đi tới Manager, báo cáo ở B đi tới Leader nhóm B.

### Xong về mặt nghiệp vụ khi
Member chỉ xem báo cáo của mình; Leader chỉ xem báo cáo nhóm mình lead; Manager xem báo cáo Leader nhưng không xem của Member; Observer không xem báo cáo Member; nộp lần hai trong ngày xử lý đúng quy ước; Leader thấy ai chưa nộp.

---

## Stage 5 — Hoàn thiện MVP và đưa lên môi trường thật

**Câu hỏi nghiệp vụ:** *Người khác dùng thử được chưa, và có an toàn để dùng không?*

### Mục tiêu nghiệp vụ
Một người không phải lập trình viên mở đường link, đăng nhập bằng tài khoản demo, đi hết luồng chính mà không gặp màn trắng hay lỗi thô.

### Yêu cầu nghiệp vụ

| # | Yêu cầu | Ghi chú |
|---|---|---|
| M1 | Truy cập qua địa chỉ công khai, dùng HTTPS | |
| M2 | Có tài khoản demo cho **cả bốn vai trò** (Admin, Manager, Leader, Member) | Mật khẩu demo lấy từ cấu hình, không đưa lên repo công khai |
| M3 | Dữ liệu demo: 1 Admin, 1 Manager, 2 Leader, vài Member, 2–3 nhóm, task ở nhiều trạng thái, báo cáo vài ngày | |
| M4 | Mọi danh sách có phân trang, trạng thái trống, trạng thái đang tải | |
| M5 | Lỗi được diễn đạt dễ hiểu: hết phiên (401), không đủ quyền (403), xung đột (409), mất kết nối | |
| M6 | Không lộ dữ liệu nhóm khác; mật khẩu không xuất hiện trong log | |
| M7 | Có sao lưu CSDL và **đã thử khôi phục** | |
| M8 | Người khác đọc README là chạy lại được | |

### Kịch bản demo trọn vẹn (dùng làm bài kiểm tra chấp nhận)
1. Admin đăng nhập → tạo nhóm "Kinh doanh" với Leader L1, Member M1, M2, Observer Mgr.
2. L1 giao task có yêu cầu file cho M1.
3. M1 bắt đầu → nộp file → chuyển chờ duyệt (cam).
4. L1 trả lại một lần kèm lý do → M1 sửa, nộp lại → L1 duyệt (xanh).
5. M1, M2 nộp báo cáo; L1 thấy ai chưa nộp, nhận xét.
6. L1 nộp báo cáo; Mgr đọc và nhận xét; Mgr **không** thấy báo cáo của M1.
7. M1 thử mở báo cáo của M2 → bị từ chối.

### Xong về mặt nghiệp vụ khi
Kịch bản demo chạy hết trên môi trường thật; ba stage trước vẫn đạt; không còn lỗi `BLOCKER`/`MAJOR` mở.

---

## Phụ lục A — Quy tắc nghiệp vụ xuyên suốt

1. **Quyền kiểm tra ở backend** cho mọi hành động; giao diện chỉ phản ánh lại.
2. **Cách ly theo nhóm:** dữ liệu nhóm A không lộ cho người chỉ thuộc nhóm B.
3. **Xoá mềm** cho dữ liệu quan trọng; lịch sử task chỉ thêm, không sửa.
4. **Xung đột đồng thời** → 409, người dùng được báo và tải lại, không mất dữ liệu lặng lẽ.
5. **Nhóm luôn có Leader;** hệ thống luôn có Admin.
6. **Dữ liệu sai** → 422 kèm danh sách trường lỗi; **không đủ quyền** → 403; **chưa đăng nhập** → 401.

## Phụ lục B — Danh sách điều cần chủ dự án chốt

Gom từ các mục **[Cần xác nhận]** ở trên. Trả lời xong thì chuyển vào `BUSINESS_OVERVIEW.md` và dùng làm nguồn cho test case.

| # | Stage | Câu hỏi | Đề xuất tạm |
|---|---|---|---|
| 1 | 1 | Có tự khoá tài khoản sau nhiều lần đăng nhập sai không? | Chưa (chỉ Admin khoá) |
| 2 | 2 | Admin có tham gia nhóm không? | Không |
| 3 | 2 | Người bị khoá vẫn nằm trong nhóm thì hiển thị thế nào? | Vẫn hiện, gắn nhãn "bị khoá" |
| 4 | 2–4 | Nhóm lưu trữ: task và báo cáo cũ còn xem được không? | Xem được, không sửa/thêm |
| 5 | 3 | Observer có xem task, tải file không? | Xem task: có; tải file: không |
| 6 | 3 | Hạn chót trong quá khứ có cho tạo không? | Không |
| 7 | 3 | Task quá hạn có tín hiệu riêng (đỏ son) không? | Có |
| 8 | 3 | Màu "chờ duyệt" (cam) chưa có trong hệ thống màu Stitch | Thêm token màu cam vào design system |
| 9 | 4 | Nộp lại báo cáo cùng ngày: cập nhật hay báo lỗi? | Cập nhật |
| 10 | 4 | Có sửa báo cáo sau ngày nộp không? | Không |
| 11 | 4 | Nhận xét có được sửa/xoá không? | Chỉ thêm |
| 12 | 4 | Admin có xem được báo cáo không? | Không (hoặc chỉ khi cần hỗ trợ — cần quyết) |
| 13 | 4 | Múi giờ tính "ngày" | `Asia/Ho_Chi_Minh` |
| 14 | 1 | Lưu refresh token trong bảng riêng hay không? | Bảng `refresh_token` (kỹ thuật, ảnh hưởng ERD) |

## Phụ lục C — Đi từ tài liệu này sang test case

Mỗi bảng quyền ở trên chuyển thẳng thành một bảng `@ParameterizedTest` trong `docs/test-cases/stage-N.md` (mục 2 của `_template.md`):

- mỗi **ô ✔** → một case "được phép";
- mỗi **ô 403** → một case "bị từ chối";
- mỗi **tình huống biên** → một case ở mục 3 của mẫu;
- bảng **chuyển trạng thái** (Stage 3) → mục 4 của mẫu.
