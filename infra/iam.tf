# IAMロール（本体）とインラインポリシー
#   ロール本体2個 ＋ 各ロールのインラインポリシー2個。
#   ポリシーの role は対応するロールへの参照にしている。

# --- ロール本体 ---

# ECSタスク用ロール（Spring Boot / Next.js のコンテナが引き受ける）
resource "aws_iam_role" "ecs_task" {
  assume_role_policy = jsonencode({
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Sid = ""
    }]
    Version = "2012-10-17"
  })
  description           = "Allows ECS tasks to call AWS services on your behalf."
  force_detach_policies = false
  max_session_duration  = 3600
  name                  = "career-support-task-role"
  path                  = "/"
  permissions_boundary  = null
  tags                  = {}
  tags_all              = {}
}

# EventBridge Scheduler 用ロール
resource "aws_iam_role" "scheduler" {
  assume_role_policy = jsonencode({
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "scheduler.amazonaws.com"
      }
      Sid = "Statement1"
    }]
    Version = "2012-10-17"
  })
  description           = null
  force_detach_policies = false
  max_session_duration  = 3600
  name                  = "career-support-scheduler-role"
  path                  = "/"
  permissions_boundary  = null
  tags                  = {}
  tags_all              = {}
}

# --- インラインポリシー ---

resource "aws_iam_role_policy" "ecs_task" {
  name = "Policy"
  policy = jsonencode({
    Statement = [{
      Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
      Effect   = "Allow"
      Resource = "arn:aws:s3:::career-support-attachments-*/attachments/*"
      }, {
      Action   = "s3:GetObject"
      Effect   = "Allow"
      Resource = "arn:aws:s3:::career-support-static-files-*/*"
      }, {
      Action   = "ses:SendEmail"
      Effect   = "Allow"
      Resource = "*"
    }]
    Version = "2012-10-17"
  })
  role = aws_iam_role.ecs_task.name
}

resource "aws_iam_role_policy" "scheduler" {
  name = "career-support-scheduler-rolePolicy"
  policy = jsonencode({
    Statement = [{
      Action   = "ecs:UpdateService"
      Effect   = "Allow"
      Resource = ["arn:aws:ecs:ap-northeast-1:460677238703:service/career-support-cluster/springboot-service", "arn:aws:ecs:ap-northeast-1:460677238703:service/career-support-cluster/nextjs-service"]
      }, {
      Action   = ["rds:StartDBInstance", "rds:StopDBInstance"]
      Effect   = "Allow"
      Resource = "arn:aws:rds:ap-northeast-1:460677238703:db:career-support-db"
    }]
    Version = "2012-10-17"
  })
  role = aws_iam_role.scheduler.name
}
