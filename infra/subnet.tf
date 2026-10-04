# サブネット（用途で命名: public=ALB / app=ECS / db=RDS / cache=Redis）
#
# 学び：
#  - import直後は subnet-xxxx というIDだけで「どれが何用か」が分からない。
#    コンソール/tfstateで用途を調べ、app_1a のように役割の分かる名前を付け直した。
#  - generate生成物には availability_zone_id（availability_zoneと競合）や ipv6/Outpost系の
#    未使用属性が大量に付く。planでエラーになった行を消し、default値のものも整理して読みやすくした。
#  - vpc_id はハードコードのID文字列を aws_vpc.main.id 参照に置換（VPCを作り直してもズレない）。

resource "aws_subnet" "public_1a" {
  availability_zone                   = "ap-northeast-1a"
  cidr_block                          = "10.0.0.0/24"
  map_public_ip_on_launch             = false
  private_dns_hostname_type_on_launch = "ip-name"
  vpc_id                              = aws_vpc.main.id
  tags = {
    Name = "public-subnet-1a"
  }
}

resource "aws_subnet" "public_1c" {
  availability_zone                   = "ap-northeast-1c"
  cidr_block                          = "10.0.5.0/24"
  map_public_ip_on_launch             = false
  private_dns_hostname_type_on_launch = "ip-name"
  vpc_id                              = aws_vpc.main.id
  tags = {
    Name = "public-subnet-1c"
  }
}

resource "aws_subnet" "app_1a" {
  availability_zone                   = "ap-northeast-1a"
  cidr_block                          = "10.0.1.0/24"
  map_public_ip_on_launch             = false
  private_dns_hostname_type_on_launch = "ip-name"
  vpc_id                              = aws_vpc.main.id
  tags = {
    Name = "app-subnet-1a"
  }
}

resource "aws_subnet" "db_1a" {
  availability_zone                   = "ap-northeast-1a"
  cidr_block                          = "10.0.2.0/24"
  map_public_ip_on_launch             = false
  private_dns_hostname_type_on_launch = "ip-name"
  vpc_id                              = aws_vpc.main.id
  tags = {
    Name = "db-subnet-1a"
  }
}

resource "aws_subnet" "db_1c" {
  availability_zone                   = "ap-northeast-1c"
  cidr_block                          = "10.0.3.0/24"
  map_public_ip_on_launch             = false
  private_dns_hostname_type_on_launch = "ip-name"
  vpc_id                              = aws_vpc.main.id
  tags = {
    Name = "db-subnet-1c"
  }
}

resource "aws_subnet" "cache_1a" {
  availability_zone                   = "ap-northeast-1a"
  cidr_block                          = "10.0.4.0/24"
  map_public_ip_on_launch             = false
  private_dns_hostname_type_on_launch = "ip-name"
  vpc_id                              = aws_vpc.main.id
  tags = {
    Name = "cache-subnet-1a"
  }
}
