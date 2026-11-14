# Cấu hình ứng dụng lưu trong AWS SSM Parameter Store dưới /soudan/<TÊN_BIẾN>.
# Máy chủ đọc toàn bộ đường dẫn này lúc khởi động và ghi ra file .env cho Docker Compose, nên không có bí mật nào nằm trong
# GitHub hay trong code. Giá trị bí mật do bạn tự đặt sau khi chạy Terraform (xem infra/README.md).
#
# LƯU Ý: giá trị do Terraform sinh (mật khẩu DB, JWT) CÓ nằm trong file terraform.tfstate. Giữ file đó ở máy bạn, không commit.

resource "random_password" "jwt" {
  length  = 64
  special = false
}

locals {
  site_url = "https://${var.domain_name}"

  # Cấu hình không bí mật: kiểu String
  plain_config = {
    SITE_ADDRESS              = var.domain_name
    FRONTEND_URL              = local.site_url
    WEBSOCKET_ALLOWED_ORIGINS = local.site_url
    DB_URL                    = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${var.db_name}"
    DB_USERNAME               = var.db_username
    DB_HOST                   = aws_db_instance.main.address
    DB_NAME                   = var.db_name
    STORAGE_TYPE              = "s3"
    S3_BUCKET                 = aws_s3_bucket.uploads.bucket
    BACKUP_BUCKET             = aws_s3_bucket.backups.bucket
    AWS_REGION                = var.region
    BACKEND_IMAGE             = "${aws_ecr_repository.backend.repository_url}:prod"
    WEB_IMAGE                 = "${aws_ecr_repository.web.repository_url}:prod"
    PULL_POLICY               = "always"
    ADMIN_EMAIL               = var.admin_email
    MAIL_HOST                 = "smtp.gmail.com"
    MAIL_PORT                 = "587"
    MAIL_SMTP_AUTH            = "true"
    MAIL_FROM                 = var.admin_email
    DEMO_ENABLED              = "true"
    OPERATING_HOURS_ENABLED   = "true"
    OPERATING_HOURS_START     = "08:30"
    OPERATING_HOURS_END       = "18:00"
    # Máy bật lúc 08:15 nên nhắc việc dời sang sau giờ bật
    REMINDER_TASK_CRON = "0 45 8 * * *"
  }

  # Bí mật do Terraform sinh
  generated_secrets = {
    DB_PASSWORD = random_password.db.result
    JWT_SECRET  = random_password.jwt.result
  }

  # Bí mật bạn tự đặt (mật khẩu ứng dụng Gmail, khoá Claude, mật khẩu Admin tạm): để chỗ giữ chỗ, Terraform không ghi đè
  manual_secrets = ["ADMIN_TEMP_PASSWORD", "MAIL_USERNAME", "MAIL_PASSWORD", "ANTHROPIC_API_KEY"]
}

resource "aws_ssm_parameter" "plain" {
  for_each = local.plain_config
  name     = "/${var.project}/${each.key}"
  type     = "String"
  value    = each.value
}

resource "aws_ssm_parameter" "generated" {
  for_each = local.generated_secrets
  name     = "/${var.project}/${each.key}"
  type     = "SecureString"
  value    = each.value
}

resource "aws_ssm_parameter" "manual" {
  for_each = toset(local.manual_secrets)
  name     = "/${var.project}/${each.key}"
  type     = "SecureString"
  value    = "DOI-GIA-TRI-NAY"

  lifecycle {
    # Bạn đặt giá trị thật bằng aws ssm put-parameter --overwrite; Terraform không được ghi đè lại
    ignore_changes = [value]
  }
}
