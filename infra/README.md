# Triển khai E-Soudan lên AWS

Hạ tầng được mô tả bằng Terraform trong [`terraform/`](terraform/). Mục tiêu: một **bản demo** chạy theo giờ làm việc
(08:30–18:00, thứ Hai–thứ Sáu), chi phí khoảng 18–22 USD/tháng, vừa gói free 200 USD / 6 tháng.

## Kiến trúc

```
Người dùng ─► Route 53 / DNS ─► Elastic IP ─► EC2 t3.small (Docker Compose)
                                                 ├─ web: Caddy (HTTPS Let's Encrypt, file tĩnh, proxy /api và /ws)
                                                 └─ backend: Spring Boot ──► RDS PostgreSQL (subnet kín)
                                                                        └─► S3 (file/ảnh, quyền qua IAM role)
GitHub Actions ─(OIDC, không khoá)─► ECR (image) ─► SSM Run Command ─► máy EC2
EventBridge Scheduler: 08:00 bật RDS, 08:15 bật EC2, 18:00 tắt EC2, 18:10 tắt RDS
```

| Thành phần | Ghi chú |
|---|---|
| VPC | 1 subnet công khai (máy chủ), 2 subnet kín (RDS); **không NAT Gateway** (đắt, không cần) |
| Security Group | máy chủ chỉ mở 80/443; **không mở SSH** (vào máy bằng SSM Session Manager); RDS chỉ nhận từ máy chủ |
| IAM role cho máy | đọc/ghi bucket uploads, ghi bucket backups, đọc cấu hình SSM, kéo image ECR |
| SSM Parameter Store | cấu hình và bí mật ứng dụng ở `/soudan/*`, máy đọc lúc khởi động |
| S3 | `uploads` (file người dùng), `backups` (sao lưu DB, xoá sau 30 ngày); chặn truy cập công khai |
| ECR | 2 kho image (backend, web), giữ 5 bản gần nhất |
| Budgets | cảnh báo email ở 50%, 80%, 100% ngân sách tháng |

## 1. Chuẩn bị một lần

1. **Cài công cụ** (PowerShell): `winget install Hashicorp.Terraform` và `winget install Amazon.AWSCLI`, mở lại terminal.
2. **Tạo người dùng IAM riêng** (đừng dùng tài khoản root): IAM → Users → tạo `soudan-deployer`, gắn quyền `AdministratorAccess`,
   tạo Access key. Chạy `aws configure` và nhập khoá **vào máy bạn** (vùng `ap-southeast-1`). Không dán khoá vào chat, vào git
   hay vào GitHub. Xong việc thì vô hiệu hoá khoá đó.
3. **Tên miền**: mua ở đâu cũng được (Route 53, Namecheap, Porkbun...). Xem mục [Tên miền](#tên-miền).
4. **Gmail**: bật xác minh 2 bước rồi tạo "Mật khẩu ứng dụng" (App password) cho `MAIL_PASSWORD`.

## 2. Dựng hạ tầng

```powershell
cd infra/terraform
copy terraform.tfvars.example terraform.tfvars   # rồi sửa domain_name, alert_email, admin_email
terraform init
terraform plan      # chỉ xem sẽ tạo gì, chưa đụng vào AWS
terraform apply     # gõ yes để tạo thật (khoảng 10 phút, RDS tạo lâu nhất)
```

Terraform ghi `terraform.tfstate` có chứa mật khẩu DB: **giữ ở máy bạn, không commit** (đã nằm trong `.gitignore`).

## 3. Sau khi `apply`

**a. Đặt bí mật thật** (Terraform chỉ tạo chỗ giữ chỗ `DOI-GIA-TRI-NAY`):

```powershell
aws ssm put-parameter --overwrite --type SecureString --name /soudan/ADMIN_TEMP_PASSWORD --value "mat-khau-tam-cua-admin"
aws ssm put-parameter --overwrite --type SecureString --name /soudan/MAIL_USERNAME        --value "ban@gmail.com"
aws ssm put-parameter --overwrite --type SecureString --name /soudan/MAIL_PASSWORD        --value "mat-khau-ung-dung-gmail"
aws ssm put-parameter --overwrite --type SecureString --name /soudan/ANTHROPIC_API_KEY    --value "khoa-claude-hoac-de-trong-bang-dau-cach"
```

Mật khẩu Admin tạm phải đổi ngay ở lần đăng nhập đầu tiên.

**b. Tên miền**: nếu để Terraform tạo zone, đặt các `name_servers` (in ở cuối `apply`) ở nhà đăng ký. Nếu `manage_dns = false`, thêm
bản ghi `A` trỏ `domain_name` về `public_ip`.

**c. Cấu hình GitHub** (repo → Settings):
- *Secrets and variables → Actions → Variables*: `AWS_DEPLOY_ROLE_ARN` = output `github_deploy_role_arn`; `SITE_URL` = `https://ten-mien`.
- *Environments → New → `production`*: bật **Required reviewers** (bạn) để mỗi lần deploy phải duyệt.

**d. Deploy lần đầu**: kho ECR còn trống nên máy chưa chạy được app. Vào *Actions → Deploy → Run workflow* (hoặc đẩy code vào
`main`), duyệt, workflow build image, đẩy ECR, rồi bảo máy kéo về. Nếu máy đang tắt, bật máy bằng tay rồi chạy lại:

```powershell
aws ec2 start-instances --instance-ids <instance_id>
aws ssm send-command --instance-ids <instance_id> --document-name AWS-RunShellScript --parameters 'commands=["systemctl restart soudan"]'
```

## 4. Vận hành

| Việc | Cách làm |
|---|---|
| Vào máy chủ | AWS Console → Systems Manager → Session Manager (không cần SSH) |
| Xem log ứng dụng | trong phiên SSM: `cd /opt/soudan && docker compose logs -f backend` |
| Bật/tắt tay ngoài giờ | `aws ec2 start-instances/stop-instances` và `aws rds start-db-instance/stop-db-instance` |
| Đổi cấu hình | `aws ssm put-parameter --overwrite ...`, rồi khởi động lại: `systemctl restart soudan` |
| Quay lại bản cũ | Actions → Deploy → Run workflow, điền mã commit (sha) đã có trên ECR vào `image_tag` |
| Sao lưu | tự động 17:30 các ngày làm việc vào bucket `backups`; chạy tay: `/opt/soudan/backup.sh` |
| Khôi phục | tải file `.sql.gz` từ S3, `gunzip -c file.sql.gz \| psql -h <rds> -U soudan -d soudan` |
| Xoá toàn bộ | `terraform destroy` (RDS giữ lại một snapshot cuối cùng tên `soudan-db-final`) |

## 5. Lưu ý về gói free

- Theo thông tin gói free: hết 6 tháng hoặc hết credit thì tài khoản có thể bị đóng nếu không nâng cấp lên trả phí.
  **Sao lưu ra ngoài AWS trước hạn**: tải bản `pg_dump` mới nhất và nội dung bucket `uploads` về máy.
- Phí đăng ký tên miền thường không trừ vào credit.
- Đặt cảnh báo ngân sách (đã có trong Terraform) và thỉnh thoảng xem trang Billing.
- IP công khai (Elastic IP) tính tiền cả khi máy tắt.

## Tên miền

| Cách | Khi nào dùng |
|---|---|
| Mua ở Route 53 | Gọn một chỗ. Đặt `hosted_zone_id` = id zone có sẵn |
| Mua nơi khác, Route 53 quản lý DNS | `manage_dns = true`, để trống `hosted_zone_id`, rồi đặt nameserver theo output |
| Mua nơi khác, tự trỏ A record | `manage_dns = false`, thêm bản ghi `A` về `public_ip` |

Caddy tự xin chứng chỉ HTTPS khi tên miền đã trỏ đúng về `public_ip` (cần cổng 80 mở, đã có sẵn).
