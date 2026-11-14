variable "project" {
  description = "Tên dự án, dùng làm tiền tố cho mọi tài nguyên."
  type        = string
  default     = "soudan"
}

variable "region" {
  description = "Vùng AWS. Singapore gần Việt Nam nhất."
  type        = string
  default     = "ap-southeast-1"
}

variable "domain_name" {
  description = "Tên miền của hệ thống, ví dụ e-soudan.site (không có https://)."
  type        = string
}

variable "alert_email" {
  description = "Email nhận cảnh báo ngân sách AWS."
  type        = string
}

variable "admin_email" {
  description = "Email của Admin đầu tiên (chỉ tạo khi hệ thống chưa có Admin)."
  type        = string
}

variable "github_repo" {
  description = "Repo GitHub được phép triển khai (định dạng chủ-sở-hữu/tên-repo)."
  type        = string
  default     = "nckPro1/E-Soudan"
}

variable "instance_type" {
  description = "Loại máy EC2. t3.small (2 GB RAM) đủ cho backend + web."
  type        = string
  default     = "t3.small"
}

variable "db_instance_class" {
  description = "Loại máy RDS."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  type    = string
  default = "soudan"
}

variable "db_username" {
  type    = string
  default = "soudan"
}

variable "manage_dns" {
  description = "true: Terraform tạo bản ghi DNS trong Route 53. false: bạn tự trỏ tên miền ở nhà đăng ký tới địa chỉ IP."
  type        = bool
  default     = true
}

variable "hosted_zone_id" {
  description = "Id hosted zone có sẵn trong Route 53 (để trống nếu muốn Terraform tạo zone mới)."
  type        = string
  default     = ""
}

# --- Lịch bật/tắt (giờ Việt Nam). Phải khớp OPERATING_HOURS_START/END hiển thị ở trang đăng nhập. ---
variable "schedule_enabled" {
  description = "false: tắt cả bốn lịch (máy chạy liên tục)."
  type        = bool
  default     = true
}

variable "schedule_timezone" {
  type    = string
  default = "Asia/Ho_Chi_Minh"
}

variable "schedule_days" {
  description = "Các ngày bật máy theo cú pháp cron của AWS, ví dụ MON-FRI hoặc * (mọi ngày)."
  type        = string
  default     = "MON-FRI"
}

variable "monthly_budget_usd" {
  description = "Ngân sách mỗi tháng (USD) để cảnh báo; gói free có 200 USD cho 6 tháng."
  type        = number
  default     = 40
}
