# ElastiCache 用サブネットグループ
resource "aws_elasticache_subnet_group" "cache" {
  name       = "career-support-cache-subnet-group"
  subnet_ids = [aws_subnet.cache_1a.id]
}

# Redis 本体
resource "aws_elasticache_cluster" "redis" {
  cluster_id         = "career-support-redis"
  engine             = "redis"
  node_type          = "cache.t3.micro"
  num_cache_nodes    = 1
  subnet_group_name  = aws_elasticache_subnet_group.cache.name
  security_group_ids = [aws_security_group.redis.id]
}
