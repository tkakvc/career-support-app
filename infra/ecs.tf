resource "aws_ecs_cluster" "main" {
  name     = "career-support-cluster"
  tags     = {}
  tags_all = {}
  configuration {
    execute_command_configuration {
      kms_key_id = null
      logging    = "DEFAULT"
    }
  }
  setting {
    name  = "containerInsights"
    value = "enhanced"
  }
}

# ============================================================
# Spring Boot タスク定義（TF管理化）
#   目的：リフレッシュトークン用の環境変数 REDIS_HOST を正しく設定する。
#   - application.yaml は `${REDIS_HOST:localhost}` を読むので、環境変数名は「REDIS_HOST」でなければならない
#     （旧タスク定義の "SPRING_REDIS_HOST" は今のアプリでは拾われないため置き換え）
#   - 値は ElastiCache リソースを参照。ベタ書きしないので destroy→再作成でアドレスが変わっても自動追従
#   apply すると新しいリビジョンが登録され、下の service がそれを使うようになる（desired_count=0 なので無停止）。
# ============================================================
resource "aws_ecs_task_definition" "springboot" {
  family                   = "career-support-springboot"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "1024"
  memory                   = "2048"
  task_role_arn            = aws_iam_role.ecs_task.arn                             # career-support-task-role（フェーズ6でimport済み）
  execution_role_arn       = "arn:aws:iam::460677238703:role/ecsTaskExecutionRole" # 未import（TF管理外）なので文字列のまま

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "springboot"
      image     = "460677238703.dkr.ecr.ap-northeast-1.amazonaws.com/career-support/spring-boot:latest"
      essential = true
      portMappings = [
        {
          name          = "springboot-8080-tcp"
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
          appProtocol   = "http"
        }
      ]
      environment = [
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://career-support-db.cmbwo5mc1uvt.ap-northeast-1.rds.amazonaws.com:5432/career_db"
        },
        {
          # リフレッシュトークンの保存先Redis。application.yaml の ${REDIS_HOST:localhost} が読む名前に一致させる。
          # 値はフェーズ7-2で作成した ElastiCache のノードエンドポイントを参照（ベタ書きしない）。
          name  = "REDIS_HOST"
          value = aws_elasticache_cluster.redis.cache_nodes[0].address
        }
      ]
      secrets = [
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "arn:aws:ssm:ap-northeast-1:460677238703:parameter/career-support/springboot/db-password"
        },
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "arn:aws:ssm:ap-northeast-1:460677238703:parameter/career-support/springboot/db-username"
        },
        {
          name      = "APP_JWT_SECRET"
          valueFrom = "arn:aws:ssm:ap-northeast-1:460677238703:parameter/career-support/springboot/app_jwt_secret"
        },
        {
          name      = "OPENAI_API_KEY"
          valueFrom = "arn:aws:ssm:ap-northeast-1:460677238703:parameter/career-support/springboot/openai_api_key"
        }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/career-support-springboot"
          "awslogs-create-group"  = "true"
          "awslogs-region"        = "ap-northeast-1"
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

# __generated__ by Terraform from "career-support-cluster/springboot-service"
resource "aws_ecs_service" "springboot" {
  availability_zone_rebalancing      = "ENABLED"
  cluster                            = aws_ecs_cluster.main.arn
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100
  desired_count                      = 0
  enable_ecs_managed_tags            = true
  enable_execute_command             = false
  force_delete                       = null
  force_new_deployment               = null
  health_check_grace_period_seconds  = 0
  iam_role                           = "/aws-service-role/ecs.amazonaws.com/AWSServiceRoleForECS"
  launch_type                        = "FARGATE"
  name                               = "springboot-service"
  platform_version                   = "LATEST"
  propagate_tags                     = "NONE"
  scheduling_strategy                = "REPLICA"
  tags                               = {}
  tags_all                           = {}
  task_definition                    = aws_ecs_task_definition.springboot.arn # TF管理のタスク定義を参照（REDIS_HOSTを含む新リビジョン）
  triggers                           = {}
  wait_for_steady_state              = null
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  deployment_controller {
    type = "ECS"
  }
  load_balancer {
    container_name   = "springboot"
    container_port   = 8080
    elb_name         = null
    target_group_arn = aws_lb_target_group.springboot.arn
  }
  network_configuration {
    assign_public_ip = true
    security_groups  = [aws_security_group.app.id] # app-sg（sg-04d8498fdcf309064）への参照
    subnets          = [aws_subnet.app_1a.id]      # app_1a（subnet-0a3c537759aa2183c）への参照
  }
}

# nextjs サービス（Redis不要なのでタスク定義は文字列参照のまま。TF書き起こしはしていない）
resource "aws_ecs_service" "nextjs" {
  availability_zone_rebalancing      = "ENABLED"
  cluster                            = aws_ecs_cluster.main.arn
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100
  desired_count                      = 0
  enable_ecs_managed_tags            = true
  enable_execute_command             = false
  force_delete                       = null
  force_new_deployment               = null
  health_check_grace_period_seconds  = 0
  iam_role                           = "/aws-service-role/ecs.amazonaws.com/AWSServiceRoleForECS"
  launch_type                        = "FARGATE"
  name                               = "nextjs-service"
  platform_version                   = "LATEST"
  propagate_tags                     = "NONE"
  scheduling_strategy                = "REPLICA"
  tags                               = {}
  tags_all                           = {}
  task_definition                    = "career-support-nextjs:1"
  triggers                           = {}
  wait_for_steady_state              = null
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  deployment_controller {
    type = "ECS"
  }
  load_balancer {
    container_name   = "nextjs"
    container_port   = 3000
    elb_name         = null
    target_group_arn = aws_lb_target_group.nextjs.arn
  }
  network_configuration {
    assign_public_ip = true
    security_groups  = [aws_security_group.app.id] # app-sg（sg-04d8498fdcf309064）への参照
    subnets          = [aws_subnet.app_1a.id]      # app_1a（subnet-0a3c537759aa2183c）への参照
  }
}
