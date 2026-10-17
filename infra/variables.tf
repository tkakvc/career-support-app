# コスト削減のための起動・停止スイッチ。
#
# このPFは選考で見せるとき以外は動かす必要が無いが、ALB・ElastiCache（Redis）は
# AWSの仕様上「停止」ができず、作ってある限り時間課金され続ける（ALB約2,800円/月・
# ElastiCache約2,200円/月）。RDSも停止は可能だが7日で自動再開してしまう。
# そのため「止める」＝「削除する」しかなく、代わりに復活を apply 1回でできるように
# このフラグで固定費のかかるリソースだけをまとめて付け外しする。
#
#   止める： terraform apply -var="enabled=false"
#   戻す：   terraform apply -var="enabled=true" -var="desired_count=1"
#
# VPC・サブネット・セキュリティグループ・S3・CloudFront・IAM・ECSクラスター・
# タスク定義は、存在するだけでは課金されない（または無料枠内）のため常に残す。
variable "enabled" {
  description = "false にすると固定費が発生するリソース（ALB・RDS・ElastiCache・ECSサービス）を削除する"
  type        = bool
  default     = true
}

# ECSサービスのタスク数。既定を0にしているのは、ALBやRDSだけ存在していて
# アプリは動かしていない現在の状態をそのまま既定の挙動にするため
# （うっかり apply してタスクが起動し課金されるのを防ぐ）。
variable "desired_count" {
  description = "ECSサービスのタスク数。0 ならタスクを起動しない"
  type        = number
  default     = 0
}
