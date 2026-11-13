# PRODUCT_DIRECTION — Định hướng sản phẩm theo Microsoft Teams

> Chủ dự án chốt ngày 13/11/2026: nghiệp vụ và giao diện của E-Soudan làm **theo kiểu Microsoft Teams** (bản Teams mới: thanh ứng dụng bên trái, danh sách ở giữa, khung nội dung bên phải).
> Tài liệu này là bản đồ tổng: mỗi mục trên thanh ứng dụng là một "app", app nào đã làm, app nào mới ở dạng kế hoạch.
> Các mục đánh dấu **[Giả định]** là mình suy ra, chưa được chủ dự án xác nhận.

## 1. Nguyên tắc giao diện

1. **Nguồn thiết kế:** màn hình Stitch "Microsoft Teams Chat Clone" (project `16852309380651674731`, screen `6e25e0c3eb6f424b80c9a2692f5fd3cd`). HTML và ảnh chuẩn nằm ở `design/homepage-assets/` (`teams.html`, `teams.png`); giao diện phải bám sát hai file này.
2. **Bố cục:** thanh tiêu đề cao 44px (logo, lùi/tới, ô tìm kiếm ở giữa, tài khoản) → thanh ứng dụng dọc rộng 52px, chỉ icon, mục đang chọn có vạch tím bên trái → cột danh sách 280px nền `#f5f5f5` → khung nội dung trắng phẳng. Trong khung: header 52px, dải thông tin, dòng tin xếp từ cũ (trên) tới mới (dưới) có vạch ngày, ô soạn ở đáy.
   Màu nhấn tím chàm `#4f52b2`, nền cửa sổ `#f0f2f5`, bong bóng `#f0f2f5` bo lớn (góc trên trái nhỏ), chữ Segoe UI cỡ 12–13px. Con dấu 報 là logo sản phẩm.
   **Cách làm:** frontend dùng **Tailwind CSS v3** (cùng phiên bản bản thiết kế) để dán nguyên markup Stitch vào component (`NavRail`, `AppLayout`, `CommunitiesLayout`, `CommunityFeedView`, `PostCard`, `ReportComposer`); logic Vue giữ nguyên. Cấu hình ở `frontend/tailwind.config.js`, `postcss.config.js`, `src/shared/styles/tailwind.css`. Không port `user-select: none` toàn cục của bản thiết kế vì người dùng cần bôi đen, sao chép nội dung báo cáo.
   **Báo cáo là một bài viết:** form viết báo cáo **phủ kín khung bên phải** (như trang tạo bài của Teams), gồm ô **Tiêu đề** ở trên và ô **Nội dung** rich-text ở dưới, thanh công cụ ở đáy: H1–H3, đậm, nghiêng, gạch chân, gạch ngang, danh sách, trích dẫn, mã, liên kết, emoji, **đính kèm file** và **chèn ảnh**. Trình soạn thảo là **Tiptap** (headless, trên ProseMirror: `@tiptap/vue-3`, `@tiptap/starter-kit`, `@tiptap/extension-placeholder`).
   Nội dung lưu dạng HTML ở `report_post.body` (V8), tiêu đề ở `report_post.title` và file ở bảng `report_attachment` (V9). Một báo cáo đính kèm tối đa 10 file, mỗi file tối đa 10 MB; loại file và "chữ ký" đầu file được kiểm tra như file nộp của task (PDF, Word, Excel, ảnh, zip). Ảnh hiện thành thumbnail trong bài, file khác là chip bấm để tải; chỉ người trong cộng đồng (và chủ cộng đồng) tải được. Server **luôn lọc HTML** bằng OWASP Java HTML Sanitizer (chống XSS), frontend lọc thêm bằng DOMPurify khi hiển thị. Báo cáo cần tiêu đề và (nội dung chữ hoặc ít nhất một file).
   **Mỗi thành viên một trang báo cáo riêng, không có feed chung:** ở sidebar, bấm mũi tên cạnh một cộng đồng để mở các **nhãn theo tên thành viên** (như các kênh trong một team của Teams); bấm nhãn tên thì mới thấy báo cáo của riêng người đó (`/communities/:id/members/:userId`). Bấm chính cộng đồng thì thấy tổng quan là các thẻ thành viên. Mọi người trong cộng đồng (và chủ cộng đồng) **xem được** trang của người khác, bình luận và thả biểu cảm được, nhưng **chỉ chính chủ mới đăng và sửa** báo cáo (thanh "Viết báo cáo" và nút Sửa chỉ có ở trang của mình; server luôn đăng báo cáo dưới danh nghĩa người đang đăng nhập). API lấy báo cáo bắt buộc có `authorId`, không còn danh sách chung cả cộng đồng.
3. **Một mẫu tương tác:** bài đăng, bình luận, trả lời một cấp, emoji reaction, chèn emoji khi gõ. Cùng một bộ component dùng cho task, báo cáo và (sau này) chat.
4. **App chưa làm vẫn có chỗ trên thanh ứng dụng**, bấm vào thấy trang "Sắp có" mô tả ngắn, để cấu trúc điều hướng ổn định ngay từ bây giờ.
5. Backend vẫn là nơi quyết định quyền; ẩn nút ở giao diện chỉ là tiện lợi.

## 2. Thanh ứng dụng và trạng thái từng app

| Thứ tự | App (Teams) | Tên trong E-Soudan | Ai thấy | Trạng thái |
|---|---|---|---|---|
| 1 | Activity | **Hoạt động** — thông báo trong ứng dụng | Mọi người trừ Admin | **Đã làm** (bản đầu, chưa có @nhắc tên) |
| 2 | Chat | **Trò chuyện** — nhóm và nhắn riêng | Mọi người trừ Admin | **Đã làm** (mục 7) |
| 3 | Communities | **Báo cáo** — cộng đồng, báo cáo hằng ngày | Manager, Leader, Member | **Đã làm** (Stage 4) |
| 4 | Tasks / Planner | **Công việc** — giao việc, nộp file, duyệt | Leader, Member | **Đã làm** (Stage 3) |
| 5 | Teams | **Nhóm** — cơ cấu nhóm, vai trò | Mọi người | **Đã làm** (Stage 2) |
| 6 | Calendar | **Lịch** | Mọi người (kể cả Admin) | **Đã làm bản đầu** (mục 8), phần mở rộng ở mục 3 là kế hoạch |
| 7 | Copilot | **Trợ lý AI** do E-Soudan tự xây | Mọi người trừ Admin | **Đã làm bản đầu** (mục 9) |
| 8 | People / Contacts | **Nhân sự** — tài khoản | Admin, Manager | **Đã làm** (Stage 1) |

Ghi chú:
- Báo cáo hằng ngày chuyển sang mô hình **cộng đồng** (xem `BUSINESS_STAGES.md`, ghi chú đầu mục Stage 4): Manager tạo cộng đồng, thêm Leader/Member; mọi thành viên thấy báo cáo của nhau; Leader không có quyền đặc biệt.
- Admin chỉ thấy Nhóm và Nhân sự (Admin không tham gia công việc hằng ngày).

## 3. Module Calendar (kế hoạch, chưa làm)

**Mục tiêu:** một lịch chung để biết ai làm gì khi nào, cùng chỗ với công việc và báo cáo.

**[Giả định] Phạm vi bản đầu:**
- Lịch cá nhân theo tháng/tuần/ngày. Hiển thị **hạn chót task** và **ngày nộp báo cáo** của chính mình (nguồn dữ liệu có sẵn từ Stage 3–4).
- Sự kiện do người dùng tạo: tiêu đề, giờ bắt đầu/kết thúc, cả ngày, mô tả, mời người tham dự (chấp nhận / từ chối).
- Lịch của cộng đồng và nhóm: sự kiện chung (họp, deadline) do Manager/Leader tạo.
- Nhắc trước sự kiện qua mục Hoạt động (phụ thuộc Stage 6).

**Ngoài phạm vi bản đầu:** đồng bộ Google/Outlook, phòng họp, họp video, sự kiện lặp phức tạp (chỉ lặp hằng ngày/tuần/tháng đơn giản nếu cần).

**Kỹ thuật dự kiến:** module `calendar` (`calendar_event`, `calendar_attendee`), lưu `timestamptz` UTC, hiển thị theo múi giờ người dùng (`app.timezone` mặc định `Asia/Ho_Chi_Minh`). Module khác (task, report) không bị sửa: `calendar` đọc hạn chót task qua `TaskApi` công khai hoặc nghe sự kiện, theo quy tắc phụ thuộc trong `ARCHITECTURE.md`.

**Câu hỏi mở cho chủ dự án:**
1. Có cần mời người ngoài công ty hoặc chỉ nội bộ?
2. Ai được tạo sự kiện chung của cộng đồng: chỉ Manager (chủ cộng đồng) hay cả Leader?
3. Có cần xuất file `.ics` không?

## 4. Module AI — trợ lý tự xây thay cho Copilot (kế hoạch, chưa làm)

**Mục tiêu:** một trợ lý hội thoại ngay trong ứng dụng, hiểu dữ liệu mà **người đang hỏi được phép xem**, giúp tiết kiệm thời gian ở các việc lặp lại.

**[Giả định] Việc trợ lý làm được ở bản đầu (chỉ đọc, không tự ý sửa dữ liệu):**
- Tóm tắt feed báo cáo của một cộng đồng trong một khoảng ngày; liệt kê vướng mắc nổi bật.
- Gợi ý nháp báo cáo hằng ngày từ task người dùng đã làm hôm nay (người dùng chỉnh rồi mới đăng).
- Tóm tắt luồng bình luận dài của một task hoặc một báo cáo.
- Trả lời câu hỏi về việc của tôi ("tuần này tôi còn task nào đến hạn?").

**Nguyên tắc bắt buộc:**
1. **Quyền kế thừa từ người hỏi:** AI chỉ đọc dữ liệu qua đúng các service đã kiểm tra quyền; không có đường tắt vào CSDL.
2. Mọi thao tác ghi (đăng báo cáo, tạo task…) cần người dùng bấm xác nhận.
3. Nội dung gửi ra dịch vụ mô hình ngôn ngữ bên ngoài là dữ liệu công ty; phải chốt chính sách trước khi bật (xem câu hỏi mở).
4. Có lịch sử hội thoại riêng của từng người, xoá được.

**Kỹ thuật dự kiến:** module `ai` (`ai_conversation`, `ai_message`), một interface `LanguageModelClient` để đổi nhà cung cấp mô hình mà không sửa nghiệp vụ; cấu hình khoá API bằng biến môi trường; gọi bất đồng bộ, hiển thị kết quả dần (streaming) trên giao diện. Lấy dữ liệu qua các `*Api` công khai của module khác.

**Câu hỏi mở cho chủ dự án:**
1. Dùng mô hình của nhà cung cấp nào, hay chạy mô hình nội bộ?
2. Có cho phép dữ liệu công ty rời khỏi hệ thống để gọi dịch vụ bên ngoài không?
3. Giới hạn chi phí hoặc số lượt hỏi mỗi người mỗi ngày?
4. Ngôn ngữ mặc định của câu trả lời: theo ngôn ngữ giao diện của người dùng (vi/ja/en)?

## 5. Thứ tự đề xuất

1. **Giao diện Teams** cho phần đã làm: khung ứng dụng, Báo cáo (cộng đồng), Công việc, Nhóm, Nhân sự. *(đang làm)*
2. Stage 6 — Hoạt động (thông báo trong ứng dụng, @nhắc tên).
3. Trò chuyện nhóm và nhắn riêng. *(đã làm, mục 7)*
4. Calendar.
5. AI.
6. Stage 9–11 như kế hoạch cũ: mail, dashboard, production.

Thứ tự 4 và 5 là **[Giả định]**; đổi theo ưu tiên của chủ dự án.

## 6. Hoạt động (thông báo trong ứng dụng) — đã làm

- **Nguồn thông báo:** các sự kiện nghiệp vụ (`TaskAssignedEvent`, `TaskSubmittedForReviewEvent`, `TaskReviewedEvent`, `TaskCommentedEvent`, `TaskCommentReactedEvent`, `ReportPostedEvent`, `ReportCommentedEvent`, `ReportReactedEvent`). Module `task` và `report` chỉ phát sự kiện, module `notification` lắng nghe (sau khi giao dịch commit, trên luồng riêng) và tạo thông báo; hai module kia không biết gì về thông báo. Mail của task vẫn chạy song song, độc lập.
- **12 loại:** được giao việc, việc chờ duyệt (Leader), việc được duyệt, việc bị trả lại, bình luận và trả lời trong task, biểu cảm trên bình luận task, báo cáo mới (chủ cộng đồng), bình luận và trả lời trên báo cáo, biểu cảm trên báo cáo và trên bình luận báo cáo.
- **Quy tắc:** không bao giờ báo cho chính người gây ra sự kiện; chỉ báo người đang hoạt động; mỗi người nhiều nhất một thông báo cho mỗi sự kiện (người được trả lời nhận "trả lời", không nhận thêm "bình luận"); thả lại cùng emoji không tạo thông báo mới; cập nhật báo cáo đã đăng trong ngày không báo lại chủ cộng đồng.
- **Dữ liệu:** bảng `notification` (V10) lưu loại, người gây ra, đối tượng (task hoặc báo cáo), tiêu đề và đoạn trích, không lưu câu chữ; giao diện tự dựng câu theo ngôn ngữ người xem (vi/ja/en).
- **API:** `GET /api/v1/notifications?unread=&page=&size=`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all`. Mỗi người chỉ thấy và thao tác được thông báo của chính mình; thông báo của người khác trả 404.
- **Giao diện:** mục Hoạt động trên thanh ứng dụng, huy hiệu số chưa đọc trên icon chuông, bộ lọc Tất cả / Chưa đọc, "Đánh dấu tất cả đã đọc", bấm vào một dòng thì đánh dấu đã đọc và mở đúng task hoặc trang báo cáo của người đó.
- **Cập nhật gần như tức thì bằng polling:** frontend hỏi lại số chưa đọc mỗi 30 giây và ngay khi quay lại tab (chưa dùng WebSocket). Chưa làm: @nhắc tên (cần ô soạn hỗ trợ trước), thông báo đẩy ra ngoài ứng dụng, tuỳ chọn tắt từng loại thông báo, tự xoá thông báo cũ.

## 7. Trò chuyện (nhắn riêng và nhắn nhóm) — đã làm

**Luật nghiệp vụ**
- Mọi vai trò nhắn tin được với nhau **trừ Admin** (Admin không thấy app Trò chuyện, gọi API trả `CHAT_ADMIN_EXCLUDED` 403).
- **Nhắn riêng cần lời mời hai chiều**: bấm tên/ảnh một người để mở thẻ hồ sơ, bấm "Nhắn tin" thì gửi lời mời; chỉ khi đối phương **chấp nhận** hai bên mới nhắn được. Trạng thái: PENDING → ACCEPTED hoặc DECLINED.
- Quyết định chưa được chủ dự án chốt (đang áp dụng, đổi được): bị **từ chối** thì bên mời không mời lại được, bên đã từ chối vẫn mời lại được; hai người **cùng mời nhau** thì tự chấp nhận; có thể **thu hồi** lời mời đang chờ.
- **Nhắn nhóm** nằm ở app **Nhóm**: mỗi nhóm có một phòng chat chung của các thành viên (gồm Observer), mở bằng tab "Trò chuyện". Nhóm đã lưu trữ thì chỉ xem lịch sử.
- **Giữ lịch sử mãi**: chỉ xoá mềm (tin đã xoá hiện "Tin nhắn đã bị xoá"), phân trang theo con trỏ, cuộn lên để tải tin cũ.
- Tin nhắn là **văn bản thuần** (tối đa 4000 ký tự), có trả lời (trích dẫn), sửa/xoá tin của mình, đính kèm file/ảnh (cùng quy tắc file của báo cáo), thả emoji (không tự thả vào tin của mình).
- Chưa đọc tính theo từng cuộc trò chuyện; hiện ở icon Trò chuyện trên thanh app, ở danh sách nhắn riêng và chấm trong danh sách nhóm. Lời mời nhắn tin cũng sinh thông báo ở **Hoạt động**.

**Kỹ thuật**
- Backend: module `chat` (V11), REST `/api/v1/chat/**`, danh bạ `/api/v1/people` (không có Admin, tài khoản khoá).
- Thời gian thực: WebSocket STOMP tại `/ws`, JWT gửi trong khung CONNECT, client chỉ được SUBSCRIBE `/user/queue/**`; mọi thao tác ghi đi qua REST. Server đẩy sau khi giao dịch commit. Mất kết nối thì tự nối lại sau 5 giây và đồng bộ bù.
- Frontend: `features/chat` (store Pinia, socket, ChatPane/ChatMessageItem/ChatComposer, ChatLayout hai cột), `features/people` (thẻ hồ sơ), app Nhóm đổi thành bố cục hai cột với tab Trò chuyện / Thành viên / Công việc.
- Chưa làm: tìm trong lịch sử, @nhắc tên, trạng thái "đang gõ", nhắn nhóm tuỳ ý ngoài nhóm công việc, ghim tin.

## 8. Lịch cá nhân (ghi chú đánh dấu theo ngày) — đã làm bản đầu

- **Ai cũng có** lịch riêng, mọi vai trò kể cả Admin. Mỗi người chỉ thấy và sửa ghi chú của mình; ghi chú của người khác coi như không tồn tại (404).
- Giao diện kiểu Teams: lưới tháng (tuần bắt đầu Thứ Hai), nút Hôm nay, chuyển tháng; ghi chú hiện thành thanh màu trong ô ngày (tối đa 3 thanh, dư thì "+N"). Bấm một ngày để xem, thêm, sửa, xoá ghi chú ở cột phải.
- Ghi chú: ngày, tiêu đề (≤ 100 ký tự), nội dung tuỳ chọn (≤ 2000 ký tự, văn bản thuần), một trong 6 màu đánh dấu. Tối đa 20 ghi chú mỗi ngày.
- Backend: module `calendar` (V12), REST `/api/v1/calendar/notes` (GET theo khoảng ngày tối đa 100 ngày, POST, PUT, DELETE).
- Quyết định mặc định (đổi được): ngày lịch lưu dạng ngày thuần, không có giờ và múi giờ; chưa có nhắc hẹn.
- Chưa làm (vẫn là kế hoạch ở mục 3): sự kiện có giờ, mời người tham gia, lịch nhóm, lặp lại, nhắc hẹn, nối với Hoạt động.

## 9. Trợ lý AI (RAG) — đã làm bản đầu

**Nguyên tắc**: AI là trợ lý đọc hiểu và tóm tắt dữ liệu **người hỏi đã có quyền xem**. Không tự hành động (không giao việc, không đăng, không gửi tin). Mọi câu trả lời kèm số nguồn bấm được để kiểm tra. Admin không dùng.

**Năm tính năng** (trang Trợ lý AI + nút "Tóm tắt AI" ở nơi liên quan)
1. **Tóm tắt báo cáo cộng đồng** (7/14/30 ngày): ai làm gì, vướng mắc cần hỗ trợ. Nút trên trang cộng đồng.
2. **Hỏi đáp có trích nguồn (RAG)** trên báo cáo, công việc, chat nhóm, ghi chú lịch; nối tiếp được vài lượt.
3. **Hỗ trợ viết**: soạn nháp báo cáo hằng ngày từ việc vừa làm; chỉnh sửa, trang trọng hoá, rút gọn, dịch Việt/Nhật/Anh. Chỉ đưa bản nháp để sao chép.
4. **Tóm tắt cuộc trò chuyện** (24 giờ gần nhất): nút trên phòng chat nhóm và trên nhắn riêng (chỉ khi người dùng chủ động bấm đúng cuộc đó).
5. **Kế hoạch làm việc** 3/7/14 ngày từ việc đang mở và ghi chú lịch: việc quá hạn, sắp đến hạn, thứ tự nên làm.

**Kiến trúc**
- Module `ai` không đọc bảng của module khác. Mỗi module có dữ liệu hiện thực `KnowledgeSource` (`common.ai`) và/hoặc một `*Api` công khai (`ReportApi`, `TaskApi`, `ChatApi`, `CalendarApi`); **module sở hữu tự lọc theo quyền** của người đang hỏi, cùng quy tắc với giao diện thường.
- Truy xuất: lấy các mẩu dữ liệu trong `app.ai.retrieval-days` ngày gần đây, chấm điểm từ khoá kiểu BM25 (bỏ dấu tiếng Việt, tách cặp ký tự cho tiếng Nhật, ưu tiên khớp tiêu đề và dữ liệu mới), kèm "bức tranh của tôi" (việc đang mở, ghi chú sắp tới). Lớp `Retriever` tách riêng để về sau thay bằng truy xuất vector (embedding + pgvector) mà không đụng phần còn lại. **Đây là truy xuất từ khoá, chưa phải vector**; Claude không có API embedding riêng nên bước này cần chọn nhà cung cấp embedding.
- Sinh câu trả lời: Claude qua Messages API. Prompt bọc dữ liệu trong thẻ `<document>` đã escape, dặn mô hình coi đó là dữ liệu chứ không phải chỉ dẫn (chống prompt injection). Câu trả lời chỉ giữ các nguồn thực sự được trích; số không có trong danh sách bị bỏ.
- Nhắn riêng **không bao giờ** nằm trong truy xuất chung; chỉ vào ngữ cảnh khi người dùng chủ động tóm tắt đúng cuộc trò chuyện đó.
- Giới hạn lượt mỗi người mỗi giờ (`app.ai.requests-per-hour`, mặc định 40); bảng `ai_usage` ghi ai dùng gì, lúc nào, đọc bao nhiêu mẩu (không lưu câu hỏi/trả lời).
- Múi giờ nghiệp vụ `app.timezone` (bean `ZoneId`) dùng cho "hôm nay" của báo cáo, lịch và AI, không dùng UTC.

**Cấu hình** (xem `docs/SETUP.md`)
- `ANTHROPIC_API_KEY` (bắt buộc để dùng thật), `AI_MODEL` (mặc định `claude-sonnet-5-5`).
- `AI_PROVIDER=mock`: bản giả lập để chạy thử giao diện, không gọi ra ngoài (không dùng ở môi trường thật).
- Chưa có khoá: ứng dụng vẫn chạy, trợ lý báo "chưa cấu hình" (503).

**Riêng tư**: dùng Claude nghĩa là nội dung báo cáo, công việc, chat nhóm, ghi chú của người hỏi **được gửi tới nhà cung cấp mô hình** trong mỗi lượt hỏi.

**Chưa làm**: truy xuất vector, trả lời dạng streaming, lưu lịch sử hội thoại AI, nút soạn nháp ngay trong ô viết báo cáo, nhắc việc tự động, chia sẻ kết quả AI.
