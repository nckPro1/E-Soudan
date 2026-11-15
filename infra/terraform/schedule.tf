# Bật/tắt máy theo giờ làm việc (EventBridge Scheduler gọi thẳng API EC2/RDS, không cần Lambda).
# Mặc định giờ Việt Nam, thứ Hai đến thứ Sáu:
#   08:00 bật RDS (cần 5-10 phút)  ->  08:15 bật EC2 (app lên sau ~2 phút)  ->  18:00 tắt EC2  ->  18:10 tắt RDS
# Phải khớp OPERATING_HOURS_START/END (08:30-18:00) hiển thị ở trang đăng nhập.
# RDS bị AWS tự bật lại sau 7 ngày tắt; vì lịch tắt chạy mỗi ngày làm việc nên không để kéo dài tới mức đó, còn lịch tắt thứ Sáu
# giữ RDS tắt cuối tuần (2 ngày).
data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "${var.project}-scheduler"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
}

data "aws_iam_policy_document" "scheduler" {
  statement {
    actions   = ["ec2:StartInstances", "ec2:StopInstances"]
    resources = [aws_instance.app.arn]
  }
  statement {
    actions   = ["rds:StartDBInstance", "rds:StopDBInstance"]
    resources = [aws_db_instance.main.arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "${var.project}-scheduler"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.scheduler.json
}

locals {
  schedules = {
    rds-start = {
      cron  = "cron(0 8 ? * ${var.schedule_days} *)"
      arn   = "arn:aws:scheduler:::aws-sdk:rds:startDBInstance"
      input = jsonencode({ DbInstanceIdentifier = aws_db_instance.main.identifier })
    }
    ec2-start = {
      cron  = "cron(15 8 ? * ${var.schedule_days} *)"
      arn   = "arn:aws:scheduler:::aws-sdk:ec2:startInstances"
      input = jsonencode({ InstanceIds = [aws_instance.app.id] })
    }
    ec2-stop = {
      cron  = "cron(0 18 ? * ${var.schedule_days} *)"
      arn   = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
      input = jsonencode({ InstanceIds = [aws_instance.app.id] })
    }
    rds-stop = {
      cron  = "cron(10 18 ? * ${var.schedule_days} *)"
      arn   = "arn:aws:scheduler:::aws-sdk:rds:stopDBInstance"
      input = jsonencode({ DbInstanceIdentifier = aws_db_instance.main.identifier })
    }
  }
}

resource "aws_scheduler_schedule" "power" {
  for_each = local.schedules

  name                         = "${var.project}-${each.key}"
  state                        = var.schedule_enabled ? "ENABLED" : "DISABLED"
  schedule_expression          = each.value.cron
  schedule_expression_timezone = var.schedule_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = each.value.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = each.value.input
  }
}
