# VPC: một subnet công khai cho máy chủ (có đường ra internet) và hai subnet kín cho cơ sở dữ liệu (không có đường ra internet).
# Không dùng NAT Gateway (~$40/tháng): database không cần gọi ra ngoài, còn máy chủ nằm ở subnet công khai.
locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "${var.project}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project}-igw" }
}

resource "aws_subnet" "public" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.1.0/24"
  availability_zone = local.azs[0]
  tags              = { Name = "${var.project}-public-a" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.project}-public" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Subnet kín: bảng định tuyến không có đường nào ra ngoài, chỉ nói chuyện được trong VPC
resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${11 + count.index}.0/24"
  availability_zone = local.azs[count.index]
  tags              = { Name = "${var.project}-private-${count.index == 0 ? "a" : "b"}" }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project}-private" }
}

resource "aws_route_table_association" "private" {
  count          = 2
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db"
  subnet_ids = aws_subnet.private[*].id
}

# --- Security Group ---
resource "aws_security_group" "app" {
  name        = "${var.project}-app"
  description = "May chu ung dung: chi mo web (80, 443)"
  vpc_id      = aws_vpc.main.id

  ingress {
    description      = "HTTP (Caddy chuyen huong sang HTTPS va xin chung chi)"
    from_port        = 80
    to_port          = 80
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description      = "HTTPS"
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description      = "HTTP/3 (QUIC)"
    from_port        = 443
    to_port          = 443
    protocol         = "udp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  # Không mở SSH: quản trị qua AWS Systems Manager (SSM Session Manager)

  egress {
    description = "Ra ngoai: ECR, S3, SSM, SMTP, API Claude, Lets Encrypt"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project}-app" }
}

resource "aws_security_group" "db" {
  name        = "${var.project}-db"
  description = "Co so du lieu: chi may chu ung dung vao duoc"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL tu may chu ung dung"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  tags = { Name = "${var.project}-db" }
}
