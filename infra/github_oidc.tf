# GitHub ActionsがAWSの長期アクセスキーを保存せず、OIDC経由の一時認証情報でECR・ECSを
# 操作できるようにする設定。①GitHubがワークフロー実行ごとに署名付きJWTを発行→
# ②configure-aws-credentialsがそのJWTでAWS STSにAssumeRoleWithWebIdentityを依頼→
# ③STSが発行者・リポジトリ/ブランチ条件を検証し、一時的なAWS認証情報を返す、という流れ。

# GitHubのOIDCエンドポイントのTLS証明書フィンガープリントを取得する
# （2025年1月以降AWS側は検証に使っていないが、リソース定義上値が必須なため取得する）
data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com"
}

# GitHubが発行するJWTを信頼する、というOIDCプロバイダ登録
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]
}

# GitHub Actionsのワークフローだけがなりすませる（AssumeRole）ロール。
# tkakvc/career-support-appのmainブランチからの実行のみに絞る
resource "aws_iam_role" "github_actions_deploy" {
  name = "career-support-github-actions-deploy"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:tkakvc/career-support-app:ref:refs/heads/main"
        }
      }
    }]
  })
}

# なりすませた後に実行してよい操作を、デプロイに必要な最小限（ECRへのpush・ECSサービスの
# 再デプロイ）に絞る。infra/iam.tfのecs_task用ロール（コンテナ自身が使う権限）とは別物
resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "career-support-github-actions-deploy-policy"
  role = aws_iam_role.github_actions_deploy.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # ECRへのログインはリポジトリ単位に絞れずアカウント全体への操作になる
        Sid      = "EcrAuth"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid    = "EcrPush"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer"
        ]
        Resource = [
          "arn:aws:ecr:ap-northeast-1:460677238703:repository/career-support/spring-boot",
          "arn:aws:ecr:ap-northeast-1:460677238703:repository/career-support/nextjs"
        ]
      },
      {
        Sid    = "EcsRedeploy"
        Effect = "Allow"
        Action = ["ecs:UpdateService", "ecs:DescribeServices"]
        Resource = [
          "arn:aws:ecs:ap-northeast-1:460677238703:service/career-support-cluster/springboot-service",
          "arn:aws:ecs:ap-northeast-1:460677238703:service/career-support-cluster/nextjs-service"
        ]
      }
    ]
  })
}

# .github/workflows/ci.ymlのdeployジョブでrole-to-assumeとしてそのまま使う
output "github_actions_deploy_role_arn" {
  value = aws_iam_role.github_actions_deploy.arn
}
