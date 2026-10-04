resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16" # このVPCが使うIPアドレスの範囲
  enable_dns_hostnames = true          # VPC内のリソースにDNSホスト名を自動で割り当てる
  enable_dns_support   = true          # VPC内でのDNS名前解決を有効にする
  instance_tenancy     = "default"     # 通常の共有ハードウェアで動かす（専有しない）

  tags = {
    Name = "career-support-vpc"
  }
}
