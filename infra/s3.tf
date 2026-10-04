# ============================================================
# S3（バケット本体＋設定は別リソース）
#   attachments  : 暗号化 / パブリックブロック / CORS
#   static_files : 暗号化 / パブリックブロック / ポリシー(CloudFront用)
#
# 学び：S3は provider v4 以降、バケットの各設定（暗号化・バージョニング等）が
#   aws_s3_bucket 本体の属性ではなく別リソースに分割された。よって本体を import しても
#   設定は付いてこない。「どの設定が実在するか」は aws s3api get-bucket-* を各設定ぶん叩き、
#   値が返れば存在（=import対象）、NotFoundなら無し、と判定した。存在した分だけ取り込んでいる。
#   （versioning と、attachmentsのpolicy / static_filesのCORS は未設定だったので無し。）
#   各設定の bucket / ポリシーのARNはバケット本体・CloudFrontへの参照にしている。
# ============================================================

# --- バケット本体 ---
resource "aws_s3_bucket" "static_files" {
  bucket              = "career-support-static-files-460677238703"
  force_destroy       = null
  object_lock_enabled = false
  tags                = {}
  tags_all            = {}
}

resource "aws_s3_bucket" "attachments" {
  bucket              = "career-support-attachments-460677238703"
  force_destroy       = null
  object_lock_enabled = false
  tags                = {}
  tags_all            = {}
}

# --- attachments の設定 ---
resource "aws_s3_bucket_server_side_encryption_configuration" "attachments" {
  bucket                = aws_s3_bucket.attachments.id
  expected_bucket_owner = null
  rule {
    bucket_key_enabled = true
    apply_server_side_encryption_by_default {
      kms_master_key_id = null
      sse_algorithm     = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "attachments" {
  bucket                  = aws_s3_bucket.attachments.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "attachments" {
  bucket                = aws_s3_bucket.attachments.id
  expected_bucket_owner = null
  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT"]
    allowed_origins = ["https://yourdomain.com"]
    expose_headers  = ["ETag"]
    id              = null
    max_age_seconds = 0
  }
}

# --- static_files の設定 ---
resource "aws_s3_bucket_server_side_encryption_configuration" "static_files" {
  bucket                = aws_s3_bucket.static_files.id
  expected_bucket_owner = null
  rule {
    bucket_key_enabled = true
    apply_server_side_encryption_by_default {
      kms_master_key_id = null
      sse_algorithm     = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "static_files" {
  bucket                  = aws_s3_bucket.static_files.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "static_files" {
  bucket = aws_s3_bucket.static_files.id
  policy = jsonencode({
    Id = "PolicyForCloudFrontPrivateContent"
    Statement = [{
      Action = "s3:GetObject"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.main.arn
        }
      }
      Effect = "Allow"
      Principal = {
        Service = "cloudfront.amazonaws.com"
      }
      Resource = "${aws_s3_bucket.static_files.arn}/*"
      Sid      = "AllowCloudFrontServicePrincipal"
    }]
    Version = "2008-10-17"
  })
}
