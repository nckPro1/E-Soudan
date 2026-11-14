# Tên miền: bản ghi A trỏ về IP cố định của máy chủ.
#  - manage_dns = true  + hosted_zone_id trống: tạo zone Route 53 mới, rồi bạn trỏ nameserver ở nhà đăng ký về (xem output name_servers)
#  - manage_dns = true  + hosted_zone_id có sẵn: dùng zone đó (tên miền mua ngay trong Route 53 đã có zone)
#  - manage_dns = false: không tạo gì, bạn tự thêm bản ghi A ở nhà đăng ký tên miền
resource "aws_route53_zone" "main" {
  count = var.manage_dns && var.hosted_zone_id == "" ? 1 : 0
  name  = var.domain_name
}

locals {
  zone_id = var.manage_dns ? (var.hosted_zone_id != "" ? var.hosted_zone_id : aws_route53_zone.main[0].zone_id) : ""
}

resource "aws_route53_record" "app" {
  count   = var.manage_dns ? 1 : 0
  zone_id = local.zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 60
  records = [aws_eip.app.public_ip]
}

resource "aws_route53_record" "www" {
  count   = var.manage_dns ? 1 : 0
  zone_id = local.zone_id
  name    = "www.${var.domain_name}"
  type    = "CNAME"
  ttl     = 300
  records = [var.domain_name]
}
