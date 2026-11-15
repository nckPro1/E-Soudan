# PostgreSQL do AWS quản lý (RDS): nằm ở subnet kín, không có địa chỉ công khai, mã hoá ổ đĩa, tự sao lưu 7 ngày.
resource "random_password" "db" {
  length  = 28
  special = false # tránh ký tự làm hỏng URL/biến môi trường
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 7
  backup_window           = "19:00-20:00" # UTC = 02:00-03:00 giờ Việt Nam
  copy_tags_to_snapshot   = true

  auto_minor_version_upgrade = true
  deletion_protection        = false
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${var.project}-db-final"

  lifecycle {
    # AWS tự nâng bản nhỏ; đừng để Terraform đòi đổi lại
    ignore_changes = [engine_version]
  }
}
