output "public_ip" {
  description = "IP cố định của máy chủ (trỏ tên miền về đây nếu manage_dns = false)."
  value       = aws_eip.app.public_ip
}

output "site_url" {
  value = local.site_url
}

output "name_servers" {
  description = "Nếu Terraform tạo zone mới: đặt các nameserver này ở nhà đăng ký tên miền."
  value       = var.manage_dns && var.hosted_zone_id == "" ? aws_route53_zone.main[0].name_servers : []
}

output "instance_id" {
  value = aws_instance.app.id
}

output "rds_identifier" {
  value = aws_db_instance.main.identifier
}

output "ecr_backend" {
  value = aws_ecr_repository.backend.repository_url
}

output "ecr_web" {
  value = aws_ecr_repository.web.repository_url
}

output "uploads_bucket" {
  value = aws_s3_bucket.uploads.bucket
}

output "backups_bucket" {
  value = aws_s3_bucket.backups.bucket
}

output "github_deploy_role_arn" {
  description = "Đặt giá trị này vào GitHub (Settings > Secrets and variables > Actions > Variables) với tên AWS_DEPLOY_ROLE_ARN."
  value       = aws_iam_role.github_deploy.arn
}

output "manual_secrets_to_set" {
  description = "Các tham số bí mật bạn phải đặt giá trị thật (xem infra/README.md)."
  value       = [for name in local.manual_secrets : "/${var.project}/${name}"]
}
