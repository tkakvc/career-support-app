# ============================================================
# セキュリティグループ
#   インターネット ──443──▶ alb ──▶ app（ECS） ──5432──▶ db(RDS) / ──6379──▶ redis(ElastiCache)
#   注意：ルール内の security_groups は SG同士が相互参照するため、参照(aws_security_group.x.id)にすると
#         循環参照エラーになる。ここは sg-xxxx のID文字列のまま残す。vpc_id だけ参照化している。
# ============================================================

resource "aws_security_group" "db" {
  description = "RDS - allows connection from app-sg only"
  egress      = []
  ingress = [{
    cidr_blocks      = []
    description      = ""
    from_port        = 5432
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = ["sg-04d8498fdcf309064"]
    self             = false
    to_port          = 5432
  }]
  name                   = "db-sg"
  revoke_rules_on_delete = null
  tags                   = {}
  tags_all               = {}
  vpc_id                 = aws_vpc.main.id
}

resource "aws_security_group" "app" {
  description = "Fargate tasks - receives from ALB only connects to RDS Redis external APIs"
  egress = [{
    cidr_blocks      = ["0.0.0.0/0"]
    description      = ""
    from_port        = 443
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = []
    self             = false
    to_port          = 443
    }, {
    cidr_blocks      = []
    description      = ""
    from_port        = 5432
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = ["sg-01d1bb68ef5ef1a9a"]
    self             = false
    to_port          = 5432
    }, {
    cidr_blocks      = []
    description      = ""
    from_port        = 6379
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = ["sg-0c540d968c944fc8a"]
    self             = false
    to_port          = 6379
  }]
  ingress = [{
    cidr_blocks      = []
    description      = ""
    from_port        = 3000
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = ["sg-0b50fb0c5fff97966"]
    self             = false
    to_port          = 3000
    }, {
    cidr_blocks      = []
    description      = ""
    from_port        = 8080
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = ["sg-0b50fb0c5fff97966"]
    self             = false
    to_port          = 8080
  }]
  name                   = "app-sg"
  revoke_rules_on_delete = null
  tags                   = {}
  tags_all               = {}
  vpc_id                 = aws_vpc.main.id
}

resource "aws_security_group" "alb" {
  description = "ALB - receives HTTP/HTTPS from internet and forwards to Fargate"
  egress = [{
    cidr_blocks      = []
    description      = ""
    from_port        = 3000
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = ["sg-04d8498fdcf309064"]
    self             = false
    to_port          = 3000
    }, {
    cidr_blocks      = []
    description      = ""
    from_port        = 8080
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = ["sg-04d8498fdcf309064"]
    self             = false
    to_port          = 8080
  }]
  ingress = [{
    cidr_blocks      = ["0.0.0.0/0"]
    description      = ""
    from_port        = 443
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = []
    self             = false
    to_port          = 443
    }, {
    cidr_blocks      = ["0.0.0.0/0"]
    description      = ""
    from_port        = 80
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = []
    self             = false
    to_port          = 80
  }]
  name                   = "alb-sg"
  revoke_rules_on_delete = null
  tags                   = {}
  tags_all               = {}
  vpc_id                 = aws_vpc.main.id
}

resource "aws_security_group" "redis" {
  description = "ElastiCache - allows connection from app-sg only"
  egress      = []
  ingress = [{
    cidr_blocks      = []
    description      = ""
    from_port        = 6379
    ipv6_cidr_blocks = []
    prefix_list_ids  = []
    protocol         = "tcp"
    security_groups  = ["sg-04d8498fdcf309064"]
    self             = false
    to_port          = 6379
  }]
  name                   = "redis-sg"
  revoke_rules_on_delete = null
  tags                   = {}
  tags_all               = {}
  vpc_id                 = aws_vpc.main.id
}
