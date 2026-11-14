# Một máy EC2 chạy backend + web (Caddy) bằng Docker Compose. Quản trị qua SSM, không mở SSH.
data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required" # bắt buộc IMDSv2, chống đánh cắp thông tin xác thực qua SSRF
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    project  = var.project
    region   = var.region
    registry = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
    compose  = file("${path.module}/../../deploy/docker-compose.yml")
    backup   = file("${path.module}/../../deploy/backup.sh")
  })
  user_data_replace_on_change = false

  tags = { Name = "${var.project}-app" }

  lifecycle {
    # AMI mới ra hằng tháng; không được thay máy (mất dữ liệu cục bộ, đổi IP) chỉ vì thế
    ignore_changes = [ami, user_data]
  }

  # Máy phải khởi động được khi cấu hình đã có sẵn trong SSM
  depends_on = [aws_ssm_parameter.plain, aws_ssm_parameter.generated, aws_ssm_parameter.manual]
}

# Địa chỉ IP cố định: giữ nguyên khi tắt/bật máy theo lịch, nên tên miền không bị lệch
resource "aws_eip" "app" {
  domain = "vpc"
  tags   = { Name = "${var.project}-app" }
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}
