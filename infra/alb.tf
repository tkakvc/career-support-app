# ============================================================
# ALB（本体 + ターゲットグループ2 + リスナー2）
#
# 学び：ALBは1リソースではなく複数に分かれる（本体=aws_lb / TG=aws_lb_target_group /
#   リスナー=aws_lb_listener / ルール=aws_lb_listener_rule）。これは「独自ARNを持つ＝別リソース」
#   という原則で見分けられる。公式の aws_lb_listener の Example Usage に本体・TG・リスナーが
#   まとめて載っており、全体像はそこで把握した。ARNは describe-* / コンソールで1つずつ取得。
# 躓き：generateが本体に subnets と subnet_mapping を両方吐き「排他」エラー。読みやすい subnets を
#   残し subnet_mapping を削除。TGには NLB向けの target_failover / target_health_state が null で
#   生成されエラー → 削除。
# 整理：subnets / security_groups / vpc_id / load_balancer_arn をID文字列から参照へ置換。
# ============================================================

# --- 本体（ALB） ---
resource "aws_lb" "main" {
  client_keep_alive                           = 3600
  customer_owned_ipv4_pool                    = null
  desync_mitigation_mode                      = "defensive"
  dns_record_client_routing_policy            = null
  drop_invalid_header_fields                  = false
  enable_cross_zone_load_balancing            = true
  enable_deletion_protection                  = false
  enable_http2                                = true
  enable_tls_version_and_cipher_suite_headers = false
  enable_waf_fail_open                        = false
  enable_xff_client_port                      = false
  enable_zonal_shift                          = false
  idle_timeout                                = 60
  internal                                    = false
  ip_address_type                             = "ipv4"
  load_balancer_type                          = "application"
  name                                        = "career-support-alb"
  preserve_host_header                        = false
  security_groups                             = [aws_security_group.alb.id]
  subnets                                     = [aws_subnet.public_1a.id, aws_subnet.public_1c.id]
  tags                                        = {}
  tags_all                                    = {}
  xff_header_processing_mode                  = "append"
  access_logs {
    bucket  = ""
    enabled = false
    prefix  = null
  }
  connection_logs {
    bucket  = ""
    enabled = false
    prefix  = null
  }
  # subnets（上）と subnet_mapping は排他（どちらか一方のみ指定可）。
  # generateが両方を吐いたため「only one of subnet_mapping,subnets can be specified」エラーになった。
  # 読みやすい subnets を残し、こちらの subnet_mapping ブロック2個を削除した。
  # subnet_mapping {
  #   allocation_id        = null
  #   ipv6_address         = null
  #   private_ipv4_address = null
  #   subnet_id            = "subnet-0bd61693b9602e7c7"
  # }
  # subnet_mapping {
  #   allocation_id        = null
  #   ipv6_address         = null
  #   private_ipv4_address = null
  #   subnet_id            = "subnet-0f64931d39b6508f3"
  # }
}

# --- ターゲットグループ（Spring Boot: 8080 / Next.js: 3000） ---
resource "aws_lb_target_group" "springboot" {
  deregistration_delay               = "300"
  ip_address_type                    = "ipv4"
  lambda_multi_value_headers_enabled = null
  load_balancing_algorithm_type      = "round_robin"
  load_balancing_anomaly_mitigation  = "off"
  load_balancing_cross_zone_enabled  = "use_load_balancer_configuration"
  name                               = "career-support-springboot-tg"
  port                               = 8080
  protocol                           = "HTTP"
  protocol_version                   = "HTTP1"
  proxy_protocol_v2                  = null
  slow_start                         = 0
  tags                               = {}
  tags_all                           = {}
  target_type                        = "ip"
  vpc_id                             = aws_vpc.main.id
  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }
  stickiness {
    cookie_duration = 86400
    cookie_name     = null
    enabled         = false
    type            = "lb_cookie"
  }
  # NLB/GWLB向け機能でALBのTGでは未使用。
  # ブロックを書くと中の値が必須なのにnullで生成され「target_failover.0.on_deregistration is required」等のエラーになる。
  # target_failover {
  #   on_deregistration = null
  #   on_unhealthy      = null
  # }
  target_group_health {
    dns_failover {
      minimum_healthy_targets_count      = "1"
      minimum_healthy_targets_percentage = "off"
    }
    unhealthy_state_routing {
      minimum_healthy_targets_count      = 1
      minimum_healthy_targets_percentage = "off"
    }
  }
  # 同じくNLB系の未使用ブロック。null生成で「...enable_unhealthy_connection_termination is required」エラー。
  # target_health_state {
  #   enable_unhealthy_connection_termination = null
  #   unhealthy_draining_interval             = null
  # }
}

resource "aws_lb_target_group" "nextjs" {
  deregistration_delay               = "300"
  ip_address_type                    = "ipv4"
  lambda_multi_value_headers_enabled = null
  load_balancing_algorithm_type      = "round_robin"
  load_balancing_anomaly_mitigation  = "off"
  load_balancing_cross_zone_enabled  = "use_load_balancer_configuration"
  name                               = "career-support-nextjs-tg"
  port                               = 3000
  protocol                           = "HTTP"
  protocol_version                   = "HTTP1"
  proxy_protocol_v2                  = null
  slow_start                         = 0
  tags                               = {}
  tags_all                           = {}
  target_type                        = "ip"
  vpc_id                             = aws_vpc.main.id
  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }
  stickiness {
    cookie_duration = 86400
    cookie_name     = null
    enabled         = false
    type            = "lb_cookie"
  }
  # NLB/GWLB向け機能でALBのTGでは未使用。
  # ブロックを書くと中の値が必須なのにnullで生成され「target_failover.0.on_deregistration is required」等のエラーになる。
  # target_failover {
  #   on_deregistration = null
  #   on_unhealthy      = null
  # }
  target_group_health {
    dns_failover {
      minimum_healthy_targets_count      = "1"
      minimum_healthy_targets_percentage = "off"
    }
    unhealthy_state_routing {
      minimum_healthy_targets_count      = 1
      minimum_healthy_targets_percentage = "off"
    }
  }
  # 同じくNLB系の未使用ブロック。null生成で「...enable_unhealthy_connection_termination is required」エラー。
  # target_health_state {
  #   enable_unhealthy_connection_termination = null
  #   unhealthy_draining_interval             = null
  # }
}

# --- リスナー（443: HTTPS / 80: HTTP→443へリダイレクト） ---
resource "aws_lb_listener" "https" {
  alpn_policy                          = null
  certificate_arn                      = "arn:aws:acm:ap-northeast-1:460677238703:certificate/c4731e06-b093-4157-9aca-67b0d48431bd"
  load_balancer_arn                    = aws_lb.main.arn
  port                                 = 443
  protocol                             = "HTTPS"
  routing_http_response_server_enabled = true
  ssl_policy                           = "ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09"
  tags                                 = {}
  tags_all                             = {}
  default_action {
    order            = 1
    target_group_arn = null
    type             = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = null
      status_code  = "403"
    }
  }
  mutual_authentication {
    ignore_client_certificate_expiry = false
    mode                             = "off"
    trust_store_arn                  = null
  }
}

resource "aws_lb_listener" "http" {
  alpn_policy                          = null
  certificate_arn                      = null
  load_balancer_arn                    = aws_lb.main.arn
  port                                 = 80
  protocol                             = "HTTP"
  routing_http_response_server_enabled = true
  tags                                 = {}
  tags_all                             = {}
  default_action {
    order            = 1
    target_group_arn = null
    type             = "redirect"
    redirect {
      host        = "#{host}"
      path        = "/#{path}"
      port        = "443"
      protocol    = "HTTPS"
      query       = "#{query}"
      status_code = "HTTP_301"
    }
  }
}
