# infra/ について

## 環境の停止・起動（コスト対策）

常時稼働させる必要が無いため、普段は落としている。`enabled` 変数（`variables.tf`）で、
固定費が発生するリソースだけをまとめて付け外しする。

```bash
cd infra

# 止める
terraform apply -var="enabled=false"

# 起動する（10〜20分。URLは https://okuyamat.click のまま変わらない）
terraform apply -var="enabled=true" -var="desired_count=1"

# ネットワーク・DBだけ起動し、アプリのタスクは動かさない
terraform apply -var="enabled=true"
```

### なぜ「停止」ではなく「削除」なのか

ALBとElastiCacheはAWSの仕様上そもそも停止できず、作ってある限り時間課金される。
RDSは停止できるが7日で自動再開してしまう。そのため「止める」＝「削除する」しかなく、
代わりに復活を `apply` 1回でできるようにしてある。

### 止めると消えるもの（計9リソース＋タスク定義の新リビジョン）

ALB・ターゲットグループ2つ・リスナー2つ・RDS・ElastiCache・ECSサービス2つ。

| リソース | 課金形態 |
|---|---|
| ALB | 稼働時間課金。停止不可 |
| RDS `db.t3.micro` + 20GB(gp2) | 稼働時間課金。停止可能だが7日で自動再開 |
| ElastiCache `cache.t3.micro` | 稼働時間課金。停止不可 |
| ECSサービス（1vCPU/2GB） | タスク実行時間のみ課金。`desired_count=0` なら発生しない |

- **RDSは削除するとデータが消える**（`skip_final_snapshot = true`・`delete_automated_backups = true`）。
  学習記録のテストデータしか入れていないため、復活時はFlywayのマイグレーションで作り直す前提

### 止めても残るもの（存在するだけでは課金されない）

VPC・サブネット・セキュリティグループ・S3（中身ごと）・CloudFront・Route53・ACM証明書・
SQS・IAMロール・ECSクラスター・タスク定義・各サブネットグループ。
コンソールから中身を見て学習する分には、止めた状態でも支障は無い。

- 停止中は `https://okuyamat.click` にアクセスすると502になる（ALBが無いため）。意図した状態
- Route53の `origin.okuyamat.click` は停止中ダミー値（`disabled.okuyamat.click`）を指す。
  レコード自体を消すとCloudFrontのオリジン設定も作り直しになり、停止・復活のたびに
  CloudFrontの再デプロイ（十数分）が発生するため、レコードは残している

### コンソールから操作する場合の注意

- GUIで作ったリソースはTerraformの管理外なので、`enabled=false` では消えない
- Terraform管理下のリソースをGUIで変更するとstateとズレる（ドリフト）。次の `apply` で
  元に戻されたり予期しない差分が出るため、設定を試す場合はTerraform側で行う

---

# 構築の経緯（学習メモ・背景）

目的：手動（コンソール）で構築済みの career-support-app のAWS環境を、後から
Terraform管理下に取り込む（brownfield / import 方式）。「既存が正」で、実物に
コードを合わせるのがゴール。最終確認は常に `terraform plan == No changes`。

## 進め方（各リソース共通のサイクル）

1. import ブロックで取り込み対象を指定
2. `terraform plan -generate-config-out=generated.tf` で resource 本体を自動生成
3. 生成物の不要属性を trim（下記の躓き参照）
4. apply で tfstate に取り込み → plan が No changes になるまで直す
5. 取り込み完了後、import ブロックを削除し、ハードコードIDを参照へ置き換えて整理

## 躓いた点と学び

- generate-config-out は「import ブロックに書いた分」しか生成しない。ALBやS3のように
  1つのAWSリソースが複数TFリソースに分かれる場合、部品を自分で洗い出して列挙する必要がある。
- 生成物には null/空/排他のゴミ属性が混ざる（VPCのipv6_netmask_length=0、
  subnetのavailability_zone_id競合、RDSのdomain_dns_ips、ALBのsubnet_mapping/target_failover等）。
  Terraformが指摘した行だけ消す。plan==No changes が唯一の安全網。
- SGのルールは相互参照（alb↔app等）するため、ルール内のSG指定を参照にすると循環参照で落ちる。
  そこだけID文字列のまま残す（security_group.tf参照）。
- ElastiCacheは削除済みだったので import ではなく create。サブネットグループだけ既存が残っており import。
