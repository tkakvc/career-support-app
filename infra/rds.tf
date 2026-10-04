# RDS（PostgreSQL）と、それを配置するDBサブネットグループ。
# 躓き：generate生成物の `domain_dns_ips = []` が「書くなら2個以上」というschema制約に反しエラー。
#   Managed AD参加用の未使用属性なので削除した（domain=null も一緒に生成されていた＝ドメイン未参加）。
# 整理：subnet_ids / db_subnet_group_name / vpc_security_group_ids をID文字列から参照へ置換。
# 注意：password は tfstate に平文で入りうるため、ここでは null（＝管理しない）。実際の認証情報は
#   ECSタスク定義の secrets（SSM Parameter Store）経由でアプリに渡している。
resource "aws_db_subnet_group" "main" {
  description = "career-support-db-subnet-group"
  name        = "career-support-db-subnet-group"
  subnet_ids  = [aws_subnet.db_1c.id, aws_subnet.db_1a.id]
  tags        = {}
  tags_all    = {}
}

resource "aws_db_instance" "main" {
  allocated_storage                     = 20
  allow_major_version_upgrade           = null
  apply_immediately                     = null
  auto_minor_version_upgrade            = true
  availability_zone                     = "ap-northeast-1a"
  backup_retention_period               = 7
  backup_target                         = "region"
  backup_window                         = "14:31-15:01"
  ca_cert_identifier                    = "rds-ca-rsa2048-g1"
  copy_tags_to_snapshot                 = true
  custom_iam_instance_profile           = null
  customer_owned_ip_enabled             = false
  database_insights_mode                = "standard"
  db_name                               = "career_db"
  db_subnet_group_name                  = aws_db_subnet_group.main.name
  dedicated_log_volume                  = false
  delete_automated_backups              = true
  deletion_protection                   = true
  domain                                = null
  domain_auth_secret_arn                = null
  domain_iam_role_name                  = null
  domain_ou                             = null
  enabled_cloudwatch_logs_exports       = []
  engine                                = "postgres"
  engine_lifecycle_support              = "open-source-rds-extended-support-disabled"
  engine_version                        = "18.3"
  final_snapshot_identifier             = null
  iam_database_authentication_enabled   = false
  identifier                            = "career-support-db"
  instance_class                        = "db.t3.micro"
  iops                                  = 0
  kms_key_id                            = "arn:aws:kms:ap-northeast-1:460677238703:key/0845239a-77a0-4522-9faf-7c2933568581"
  license_model                         = "postgresql-license"
  maintenance_window                    = "fri:20:12-fri:20:42"
  manage_master_user_password           = null
  max_allocated_storage                 = 100
  monitoring_interval                   = 0
  multi_az                              = false
  network_type                          = "IPV4"
  option_group_name                     = "default:postgres-18"
  parameter_group_name                  = "default.postgres18"
  password                              = null # sensitive
  password_wo                           = null # sensitive
  password_wo_version                   = null
  performance_insights_enabled          = false
  performance_insights_retention_period = 0
  port                                  = 5432
  publicly_accessible                   = false
  replicate_source_db                   = null
  skip_final_snapshot                   = true
  storage_encrypted                     = true
  storage_throughput                    = 0
  storage_type                          = "gp2"
  tags                                  = {}
  tags_all                              = {}
  upgrade_storage_config                = null
  username                              = "postgres"
  vpc_security_group_ids                = [aws_security_group.db.id]
}
