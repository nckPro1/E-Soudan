# Hai bucket S3, đều chặn truy cập công khai và mã hoá mặc định.
#  - uploads: ảnh và file người dùng tải lên (app đọc/ghi bằng IAM role của máy chủ)
#  - backups: bản sao lưu cơ sở dữ liệu, tự xoá sau 30 ngày
locals {
  uploads_bucket = "${var.project}-uploads-${data.aws_caller_identity.current.account_id}"
  backups_bucket = "${var.project}-backups-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket" "uploads" {
  bucket = local.uploads_bucket
}

resource "aws_s3_bucket" "backups" {
  bucket = local.backups_bucket
}

resource "aws_s3_bucket_public_access_block" "uploads" {
  bucket                  = aws_s3_bucket.uploads.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "backups" {
  bucket                  = aws_s3_bucket.backups.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id

  rule {
    id     = "expire-old-db-backups"
    status = "Enabled"

    filter {
      prefix = "db/"
    }

    expiration {
      days = 30
    }
  }
}

# Kho image Docker riêng; chỉ giữ 5 image gần nhất để khỏi tốn tiền lưu trữ
resource "aws_ecr_repository" "backend" {
  name                 = "${var.project}/backend"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "web" {
  name                 = "${var.project}/web"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "keep_five" {
  for_each   = { backend = aws_ecr_repository.backend.name, web = aws_ecr_repository.web.name }
  repository = each.value

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Giu 5 image gan nhat"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}
