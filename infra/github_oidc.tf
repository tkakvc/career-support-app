# 参考：https://zenn.dev/not75743/articles/10014b21dfb3a0
#   （【GitHub Actions】OpenID Connectを使用してAWSの認証を行う構成をTerraformで用意する）
#
# ============================================================
# GitHub Actions が、AWSの長期パスワード（アクセスキー。AWSにログインするための
# ID・パスワードの組。実際にはAccessKeyId・SecretAccessKeyという2つの文字列）を
# 一切保存せずに ECR・ECS を操作できるようにするための設定（OIDC＝OpenID Connect。
# 「これは誰か」を証明する仕組みの業界標準規格）。
#
# 全体の流れ（誰が・何をするか）：
#   ①GitHub が、ワークフロー実行のたびに「これはtkakvc/career-support-appの
#     mainブランチの実行です」という内容の、署名付きの使い捨て証明書（JWT）を発行する
#   ②その証明書を、GitHub Actionsのランナー（ジョブ専用に使い捨てで用意される
#     仮想マシン）の中で動く configure-aws-credentials というプログラム
#     （AWS社がGitHub上に公開しているだけの、ただのプログラム＝GitHub Action）が受け取る
#   ③そのプログラムが、証明書を持ってAWSのSTS（AWSが常設しているサーバー）に
#     「これと引き換えに、一時的なAWSの鍵がほしい」と依頼する
#     （sts:AssumeRoleWithWebIdentity というAPI呼び出し。詳しくは下のロール定義部分で説明）
#   ④STSは、証明書が本当にGitHubの発行したものか（署名）と、
#     「career-support-appのmainブランチ」から来たものか（このファイルの条件）を確認する
#   ⑤OKなら、STSは元の証明書とは別物の、数十分だけ有効な一時キー
#     （こちらも実体はAccessKeyId・SecretAccessKeyに加えてSessionTokenという3点セット）
#     を新規発行して返す
#   ⑥以降の docker push（作ったdockerイメージをECRという保管場所にアップロードする
#     コマンド）・ecs update-service（ECSに「新しいイメージを取り直して起動し直して」
#     と指示するコマンド）は、この新しい一時キーを使って実行される
#
# このファイルが担当しているのは①〜⑤の「AWS側の受け入れ準備」の部分。
# ⑥のワークフロー（.github/workflows/ci.yml）は別ファイル。
#
# 参考：memo/github_actions/OIDCの仕組み.html（この流れをもっと詳しく図解したもの）
#
# 【このファイルに出てくる略語・専門用語】
#   OIDC = OpenID Connect（上に説明あり）
#   JWT  = JSON Web Token。「誰が」「いつまで有効か」等の情報をJSON形式のテキストで持ち、
#          改ざんできないよう署名が付いた文字列。今回の「証明書」の実体がこれ
#   ECR  = Elastic Container Registry。dockerイメージ（アプリを動かすのに必要な
#          ファイル一式をまとめたもの）を保管しておくAWSのサービス
#   ECS  = Elastic Container Service。dockerイメージを実際にコンテナとして動かす
#          （＝アプリをサーバー上で起動する）AWSのサービス
#   STS  = Security Token Service。一時的な認証情報を専門に発行する、AWSが
#          常設しているサーバー（このAWSアカウント専用ではなく、AWS全体で共通の設備）
#   IAM  = Identity and Access Management。「誰が」「何をしてよいか」を管理する
#          AWSのサービス。このファイルで作る「ロール」もIAMの機能の1つ
#   ARN  = Amazon Resource Name。AWSの中の全リソースに1つずつ付く、世界で重複しない
#          住所のような文字列（例：末尾のoutputに出てくるロールのARN）
#
# 【このファイルで繰り返し出てくる token.actions.githubusercontent.com とは】
#   GitHubがOIDC専用に運営しているサーバーのアドレス。①のJWT発行は実際にこのサーバーが
#   行っており、発行されたJWTの iss（発行者）フィールドにはこのURLがそのまま入る。
#   このサーバー配下には、署名の検算に使う公開鍵の一覧（/.well-known/jwks）も公開されている。
#   このファイル内で同じ文字列が3箇所に出てくるが、それぞれ役割が違う：
#     - data.tls_certificate の url          … 実際にこの住所へHTTPS接続する（通信先の指定）
#     - aws_iam_openid_connect_provider の url … 「この住所からのJWTは信用する」とAWSに登録する
#     - 信頼ポリシーの条件キーの接頭辞          … AWS IAM側の書き方の決まりで、「どのOIDCプロバイダの
#       （"token.actions.githubusercontent.com:aud" 等） トークンのaud/subフィールドを見るか」を、
#                                              プロバイダのURLを接頭辞にして指定する仕様になっている
#
# 【Terraformの"data"と"resource"の違い】
#   下に data "tls_certificate" ... という書き方と、resource "aws_iam_role" ... という
#   書き方の両方が出てくる。data は「AWS（や外部サイト）に既にある情報を読み取るだけで、
#   何も新しく作らない」宣言。resource は「Terraformがこれから新しく作って管理する」宣言。
#   tls_certificate はGitHubのサーバーから証明書の情報を読み取るだけなので data、
#   IAMロールなどは実際にAWS上に新規作成するので resource になっている。
# ============================================================

# ---- 準備：GitHubを名乗る証明書が本物か検証するための材料を取得する ----
#
# JWT（署名付き証明書）の署名が本物かどうかは、発行元（ここではGitHub）が公開している
# 「公開鍵」を使って検算する。この公開鍵を安全に受け取るためにHTTPS通信を使うが、
# その際の相手（token.actions.githubusercontent.com。上の説明参照）の証明書の「指紋」
# （thumbprint。SHA-1というハッシュ関数＝どんなデータも決まった長さの短い文字列に
#  要約する計算方法で、証明書の中身を要約した値。中身が少しでも違えば指紋も別の値になる）
# をAWS側に登録しておく必要がある。
#
# 値を固定の文字列としてこのファイルに書き込まず、実際に
# https://token.actions.githubusercontent.com に接続して、その場で指紋を取得する方式にした
# （2025年1月以降、AWS側はこの指紋の値自体を実質的にはもう検証に使っていないが、
#  Terraformのリソース定義上は値が必須のため、正しい値を自動で取ってくるようにしている）。
data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com"
}

# ---- ①の受け皿：「GitHubが発行する証明書は信用する」とAWSに登録する ----
#
# これをやらないと、AWSはGitHubが発行した証明書を見ても「知らない発行元だ」と
# 突き返してしまう。いわばGitHubの印鑑登録。1つのAWSアカウントにつき、
# 同じURLのプロバイダは1個しか登録できない（既に登録済みなら重複エラーになる）。
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"] # この証明書は「AWSのSTSに見せるためのもの」という宛先の指定
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]
}

# ---- ③〜④の受け皿：証明書と引き換えに一時キーを受け取れる「役（ロール）」を作る ----
#
# IAM Role（ロール）とは、AWSの中で「特定の操作をしてよい権限のまとまり」に付けた名前。
# 人間のユーザーのように常にログインしているものではなく、「一時的になりすます（assume）」
# ことで初めて使える。今回はGitHub Actionsのワークフローだけがこのロールになりすませるように、
# assume_role_policy（信頼ポリシー＝「誰になりすまし=assumeを許可するか」の条件）で絞っている。
resource "aws_iam_role" "github_actions_deploy" {
  name = "career-support-github-actions-deploy"
  assume_role_policy = jsonencode({
    # IAMポリシーの記法自体のバージョン指定。中身の年月日は実際の日付とは無関係で、
    # AWSが定めた「ポリシー言語のこのバージョンで書いています」という決まり文句。
    # 現時点ではこの "2012-10-17" 以外の値を書くことはまず無い。
    Version = "2012-10-17"
    Statement = [{
      # Effect：この許可のかたまりが「許可する(Allow)」か「拒否する(Deny)」かの指定
      Effect = "Allow"
      # Principal：この許可が「誰に対するものか」の指定。AWS(IAMユーザー等)・Service(AWSの別サービス)・
      # Federated(AWSの外にある別の認証の仕組み)など、相手の種類によって書き方が変わる。
      #
      # Federated＝「federate（連合させる）」の過去分詞。語源はラテン語 foedus（条約・同盟）で、
      # 「連邦(federal)」と同じ語根。本来別々の独立した組織が、協定で結びついた状態を指す。
      # GitHubはAWSの一部ではなく全く別の会社が運営する独立した認証の仕組みだが、
      # 上のOIDCプロバイダ登録という「協定」で結びつけたので、ここでは
      # Federated = aws_iam_openid_connect_provider.github.arn
      # （＝上で登録したGitHubのOIDCプロバイダからの証明書を使ってなりすましてよい）と書ける。
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      # sts:AssumeRoleWithWebIdentity：「外部の証明書（Web Identity）と引き換えに、
      # このロールに一時的になりすませてほしい（AssumeRole）」という、AWS STSに対する依頼の名前
      Action = "sts:AssumeRoleWithWebIdentity"
      # Condition：Principal・Actionの条件を満たしていても、さらにここに書いた条件も
      # 両方満たさない限り許可しない、という追加の絞り込み
      Condition = {
        # aud（audience＝宛先）が「sts.amazonaws.com」になっている証明書だけを受け付ける
        # （GitHubが発行する証明書のaudには、STSに登録した client_id_list の値がそのまま入る）
        # StringEquals：値が完全に一致する場合だけ許可する演算子
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        # sub（subject＝この証明書が誰の実行を表しているか）が、下の文字列と完全一致する場合だけ許可する。
        # GitHubが発行する証明書のsubには「repo:オーナー名/リポジトリ名:ref:refs/heads/ブランチ名」
        # という形の文字列が入る。ここではtkakvc/career-support-appのmainブランチだけを許可しているので、
        # 他のブランチや、同じAWSアカウントに同居している別PF（csv-data-pipeline）からの証明書は、
        # subの文字列が一致せず弾かれる。
        # StringLike：StringEqualsと似ているが、ワイルドカード（*等）による部分一致も書ける演算子。
        # 今回は完全一致の文字列しか書いていないが、将来「どのブランチでも許可」のように
        # 範囲を広げたくなった場合にワイルドカードへ変更しやすいよう、audと使い分けている。
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:tkakvc/career-support-app:ref:refs/heads/main"
        }
      }
    }]
  })
}

# ---- ⑤〜⑥で「一時キーになりすませた後、実際に何をしてよいか」を決める ----
#
# 上のassume_role_policy（信頼ポリシー）が「誰がなりすませるか」の入口の条件なのに対し、
# こちらは「なりすませた後、その一時キーで実際に何をしてよいか」という中身の権限。
# 別物なので2つのリソースに分かれている。ここではデプロイに必要な最小限
# （ECRへのpushと、ECSサービスへの再デプロイ指示だけ）に絞っている。
# 似た名前の infra/iam.tf の ecs_task 用ロールとは全くの別物（あちらはECS上で
# 実際に動くSpring Boot/Next.jsコンテナ自身が使う権限。こちらはGitHub Actions側が使う権限）。
resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "career-support-github-actions-deploy-policy"
  role = aws_iam_role.github_actions_deploy.name
  policy = jsonencode({
    Version = "2012-10-17" # 上と同じ、決まり文句のバージョン指定
    Statement = [
      {
        # Sid（Statement ID）：この許可のかたまりに付けた、識別用の名前。
        # 動作そのものには影響せず、AWSコンソールやログで「これは何の許可か」を
        # 分かりやすくするためだけに付けている。
        #
        # ECRへのログイン処理（docker loginに相当）は、AWSの仕様上リポジトリ単位に絞れず
        # アカウント全体に対する操作になる。ここだけResourceが"*"（全て）になっているのはそのため。
        Sid      = "EcrAuth"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        # 実際にdocker push（イメージのアップロード）をするのに必要な一連の操作。
        # Resourceでcareer-support/spring-boot・nextjsの2つのリポジトリだけに絞っている
        # （このAWSアカウントに存在する他のECRリポジトリ = 別PFのcsv-data-pipeline用と
        #  思われる cost-csv-backfill 等には触れない）。
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
        # 新しいイメージをpushした後、ECSに「新しいイメージを取り直して再起動して」と指示するのに必要。
        # Resourceでこのアプリの2サービスだけに絞っている（他のECSクラスタ・サービスには触れない）。
        Sid      = "EcsRedeploy"
        Effect   = "Allow"
        Action   = ["ecs:UpdateService", "ecs:DescribeServices"]
        Resource = [
          "arn:aws:ecs:ap-northeast-1:460677238703:service/career-support-cluster/springboot-service",
          "arn:aws:ecs:ap-northeast-1:460677238703:service/career-support-cluster/nextjs-service"
        ]
      }
    ]
  })
}

# ---- test-backend用の別ロール：デプロイ用ロールとは分離する ----
#
# 上のgithub_actions_deployロールはsubの条件がmainブランチに完全一致する場合のみ許可している
# （デプロイをmain以外から誤発火させないための意図的な制限）。
# しかしCI（.github/workflows/ci.ymlのtest-backendジョブ）はstudy・feature/**等、全ブランチの
# pushで動く。BackendApplicationTests.contextLoads()はAiJobWorkerの@SqsListenerが
# career-support-ai-jobsキューにGetQueueUrl・GetQueueAttributesを呼べないと
# QueueAttributesResolvingExceptionで失敗するため、全ブランチでこの2つの権限が要る。
# デプロイ用ロールのsub条件をmain限定から緩めるのではなく、全ブランチから使える別ロールを
# 新設し、権限もSQSの読み取り関連だけに絞ることで、デプロイ権限は引き続きmain限定のまま守る。
resource "aws_iam_role" "github_actions_test" {
  name = "career-support-github-actions-test"
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
        # StringLike + ワイルドカード：mainに限定せず、このリポジトリの全ブランチからの
        # 実行を許可する（refs/heads/以降が何であっても一致する）
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:tkakvc/career-support-app:ref:refs/heads/*"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_actions_test" {
  name = "career-support-github-actions-test-policy"
  role = aws_iam_role.github_actions_test.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      # contextLoads()はキューにメッセージを送らない（SendMessage不要）・DLQも見ない
      # （最小権限の原則）。ReceiveMessage/DeleteMessageは、起動直後に@SqsListenerが
      # 始めるロングポーリングがGetQueueAttributesの直後に動くため、無いと別のエラーで失敗する。
      Sid      = "SqsTestAccess"
      Effect   = "Allow"
      Action   = ["sqs:GetQueueUrl", "sqs:GetQueueAttributes", "sqs:ReceiveMessage", "sqs:DeleteMessage"]
      Resource = ["arn:aws:sqs:ap-northeast-1:460677238703:career-support-ai-jobs"]
    }]
  })
}

# test-backendジョブの aws-actions/configure-aws-credentials で
# role-to-assume としてそのまま使う
output "github_actions_test_role_arn" {
  value = aws_iam_role.github_actions_test.arn
}

# このロールのARN（AWSの中での一意な住所のような文字列）を、terraform apply後に
# ターミナルへ表示させておくための出力。.github/workflows/ci.ymlのdeployジョブで
# 「role-to-assume: このARN」という形でそのまま貼り付けて使う。
output "github_actions_deploy_role_arn" {
  value = aws_iam_role.github_actions_deploy.arn
}
