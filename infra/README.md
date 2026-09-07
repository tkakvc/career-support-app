# infra/ について（学習メモ・背景）

目的：手動（コンソール）で構築済みの career-support-app のAWS環境を、後から
Terraform管理下に取り込む（brownfield / import 方式）。「既存が正」で、実物に
コードを合わせるのがゴール。最終確認は常に `terraform plan == No changes`。

## 進め方（各リソース共通のサイクル）

1. import ブロックで取り込み対象を指定
2. `terraform plan -generate-config-out=generated.tf` で resource 本体を自動生成
3. 生成物の不要属性を trim（下記の躓き参照）
4. apply で tfstate に取り込み → plan が No changes になるまで直す
5. 取り込み完了後、import ブロックを削除し、ハードコードIDを参照へ置き換えて整理

## 躓いた点と学び（詳細は memo/terraform/09-躓きと学び.md）

- generate-config-out は「import ブロックに書いた分」しか生成しない。ALBやS3のように
  1つのAWSリソースが複数TFリソースに分かれる場合、部品を自分で洗い出して列挙する必要がある。
- 生成物には null/空/排他のゴミ属性が混ざる（VPCのipv6_netmask_length=0、
  subnetのavailability_zone_id競合、RDSのdomain_dns_ips、ALBのsubnet_mapping/target_failover等）。
  Terraformが指摘した行だけ消す。plan==No changes が唯一の安全網。
- SGのルールは相互参照（alb↔app等）するため、ルール内のSG指定を参照にすると循環参照で落ちる。
  そこだけID文字列のまま残す（security_group.tf参照）。
- ElastiCacheは削除済みだったので import ではなく create。サブネットグループだけ既存が残っており import。
