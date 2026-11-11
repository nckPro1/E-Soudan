# Stage 5 — Hoàn thiện MVP và triển khai lần đầu (từng bước)

> Mục tiêu: MVP chạy được trên môi trường thật và demo được. Xem `IMPLEMENTATION_PLAN.md` Stage 5 và `ARCHITECTURE.md` mục 13–14.
> Điều kiện vào stage: Stage 4 xong (`stage-4-done`).
> Lưu ý: phần AWS dưới đây là **các bước ở mức hướng dẫn**; chi tiết lệnh và giao diện AWS thay đổi, đối chiếu tài liệu chính thức của AWS khi làm.

## Tổng quan thứ tự

`5.1 rà soát chức năng → 5.2 trải nghiệm → 5.3 dữ liệu demo → 5.4 container hoá → 5.5 cấu hình môi trường → 5.6 hạ tầng AWS → 5.7 CI/CD → 5.8 giám sát và sao lưu → 5.9 README → 5.10 cổng`

---

### 5.1 Rà soát chức năng MVP — [CẢ HAI]
- Chạy lại toàn bộ `docs/test-cases/stage-1..4.md`, đặc biệt các case MANUAL. Ghi lỗi vào `docs/reviews/stage-5.md`.
- Rà soát bảo mật cơ bản: mọi endpoint có kiểm tra quyền; không lộ dữ liệu nhóm khác; mật khẩu không xuất hiện trong log; giới hạn kích thước request.
- **Xong khi:** không còn lỗi `BLOCKER`/`MAJOR` mở.

### 5.2 Hoàn thiện trải nghiệm — [AI, BẠN duyệt]
- Mọi danh sách có phân trang, trạng thái trống, trạng thái đang tải.
- Thông báo lỗi dễ hiểu (từ `ErrorCode`), xử lý 401 (hết phiên), 403, 409 thống nhất.
- Thông báo lỗi khi mất kết nối.
- **Xong khi:** đi hết các màn hình, không có màn trắng hoặc lỗi thô hiện ra người dùng.

### 5.3 Dữ liệu demo — [AI]
- Bộ dữ liệu cho profile `dev`/`demo`: 1 Admin, 1 Manager, 2 Leader, vài Member, 2–3 nhóm, vài task ở các trạng thái, báo cáo vài ngày. Mật khẩu demo lấy từ cấu hình, không đưa vào code công khai.
- **Xong khi:** khởi động với profile demo là có sẵn dữ liệu để thao tác.

### 5.4 Container hoá — [CẢ HAI]
- `backend/Dockerfile` nhiều tầng (build bằng Gradle + JDK 21, chạy bằng JRE nhỏ gọn, chạy bằng user không phải root). Đặt `-Xmx` khoảng 75% bộ nhớ container.
- Frontend: build ra file tĩnh (`npm run build`); phục vụ bằng nginx hoặc đưa lên S3 + CloudFront ở bước 5.6.
- `docker-compose.prod.yml` để thử cả hệ thống bằng container trên máy.
- **Xong khi:** `docker compose -f docker-compose.prod.yml up` chạy cả DB, backend, frontend; vào được qua trình duyệt.

### 5.5 Cấu hình môi trường — [BẠN]
- Profile `prod`: mọi bí mật (JWT secret, mật khẩu DB, SMTP) đến từ biến môi trường hoặc AWS Secrets Manager. Không có giá trị mặc định nhạy cảm trong repo.
- CORS chỉ cho phép tên miền frontend thật. Bật log có cấu trúc; Actuator chỉ mở `health` (và `info`).
- Thư mục lưu file nộp: dùng ổ đĩa bền vững ở giai đoạn đầu; đổi sang S3 ở stage 11.
- **Xong khi:** khởi động `prod` cục bộ bằng biến môi trường thành công; thiếu biến bí mật thì khởi động **thất bại có thông báo rõ**.

### 5.6 Hạ tầng AWS — [BẠN làm theo hướng dẫn, AI hỗ trợ giải thích]
Chọn một trong hai, bắt đầu từ lựa chọn rẻ hơn:
- **Lựa chọn A (đơn giản, rẻ hơn): một EC2 chạy Docker Compose.** Tạo instance, cài Docker, mở cổng 80/443 trong security group, chạy `docker-compose.prod.yml`, dùng reverse proxy (nginx hoặc Caddy) kèm chứng chỉ HTTPS. CSDL có thể là container (chỉ để thử) hoặc RDS.
- **Lựa chọn B (kiến trúc đích):** ECR chứa image; ECS Fargate chạy backend sau Application Load Balancer; RDS PostgreSQL trong subnet riêng; frontend ở S3 + CloudFront; chứng chỉ HTTPS bằng ACM.
- Kiểm tra chi phí trước khi bật (ALB, NAT Gateway, RDS tính theo giờ). Đặt cảnh báo ngân sách.
- Nếu dùng WebSocket sau này (stage 7), nhớ kiểm tra timeout kết nối rảnh của ALB.
- **Xong khi:** truy cập được ứng dụng qua HTTPS bằng tên miền hoặc địa chỉ công khai; Admin đăng nhập được.

### 5.7 CI/CD — [CẢ HAI]
- Mở rộng `.github/workflows/ci.yml`: sau khi test qua trên nhánh `main`, build image, đẩy lên registry (ECR), rồi triển khai (cập nhật dịch vụ ECS, hoặc SSH và `docker compose pull && up -d` với lựa chọn A).
- Bí mật CI lưu ở GitHub Secrets. Quyền AWS dùng vai trò tối thiểu cần thiết.
- **Xong khi:** đẩy một thay đổi nhỏ lên `main` tự triển khai và thấy thay đổi trên môi trường thật.

### 5.8 Giám sát và sao lưu cơ bản — [BẠN]
- Health check của Actuator được cấu hình ở load balancer/ECS. Log gom về CloudWatch (hoặc xem bằng `docker logs` ở lựa chọn A).
- Sao lưu CSDL tự động (RDS snapshot hoặc `pg_dump` định kỳ) và **thử khôi phục** một lần.
- **Xong khi:** biết xem log khi có lỗi; có bản sao lưu khôi phục được.

### 5.9 README và tài liệu — [AI soạn, BẠN duyệt]
- `README.md` ở gốc: giới thiệu, cách chạy cục bộ, cách chạy test, cách triển khai, cấu trúc thư mục, danh sách biến môi trường (tên và ý nghĩa, không có giá trị bí mật), tài khoản demo.
- Cập nhật bảng tiến độ trong `IMPLEMENTATION_PLAN.md`.
- **Xong khi:** người khác chỉ đọc README là chạy lại được.

### 5.10 Cổng stage — [REVIEWER, TESTER, BẠN]
- Reviewer (tập trung bảo mật và cấu hình), tester thử phá trên môi trường thật ở mức nhẹ, `scripts/verify-stage.sh 5 --e2e`, chạy thủ công toàn bộ luồng chính trên môi trường thật, tag `stage-5-done` (**MVP hoàn thành**).

## Xong stage khi (tóm tắt)
Truy cập được ứng dụng qua URL công khai; có tài khoản demo cho cả bốn vai trò; luồng chính (nhóm → task → báo cáo) chạy được trên môi trường thật; CI/CD tự triển khai; README đủ để chạy lại; bí mật không nằm trong repo.

## Khái niệm sẽ gặp
Dockerfile nhiều tầng, biến môi trường và Spring profile, reverse proxy và HTTPS, vai trò IAM tối thiểu, health check, sao lưu và khôi phục CSDL, quản lý bí mật.
