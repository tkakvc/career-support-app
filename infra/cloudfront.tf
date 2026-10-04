# CloudFront ディストリビューション
# 学び：アカウントに複数あり（別PFの csv-data-pipeline 用も同居）。どれが本アプリ用かは
#   Aliases（ドメイン）と Origins（配信元）で判定。okuyamat.click かつ Origins が
#   career-support の ALB/S3 を指す E2SKFXAMYISTFB が本アプリ用。もう一方(csv.okuyamat.click)は
#   別PFで現用中なので import も削除もしない。
# 補足：オリジンのドメイン・ACM証明書ARN等は、参照化すると差分リスクがあるため文字列のまま残した
#   （証明書は us-east-1 必須で、TF管理には別provider(alias)が要る。既存importでは費用対効果が低い）。
resource "aws_cloudfront_distribution" "main" {
  aliases             = ["okuyamat.click", "www.okuyamat.click"]
  comment             = null
  default_root_object = null
  enabled             = true
  http_version        = "http2"
  is_ipv6_enabled     = true
  price_class         = "PriceClass_All"
  retain_on_delete    = false
  staging             = false
  tags = {
    Name = "career-support-app"
  }
  tags_all = {
    Name = "career-support-app"
  }
  wait_for_deployment = true
  web_acl_id          = "arn:aws:wafv2:us-east-1:460677238703:global/webacl/CreatedByCloudFront-7007dae0/ac0a7e04-040b-4b82-b5eb-795c45467d09"
  default_cache_behavior {
    allowed_methods            = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cache_policy_id            = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    default_ttl                = 0
    field_level_encryption_id  = null
    max_ttl                    = 0
    min_ttl                    = 0
    origin_request_policy_id   = "216adef6-5c7f-47e4-b989-5492eafa07d3"
    realtime_log_config_arn    = null
    response_headers_policy_id = null
    smooth_streaming           = false
    target_origin_id           = "career-support-alb-1434304583.ap-northeast-1.elb.amazonaws.com-mrpxe2ctl6d"
    trusted_key_groups         = []
    trusted_signers            = []
    viewer_protocol_policy     = "redirect-to-https"
    grpc_config {
      enabled = false
    }
  }
  ordered_cache_behavior {
    allowed_methods            = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cache_policy_id            = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    default_ttl                = 0
    field_level_encryption_id  = null
    max_ttl                    = 0
    min_ttl                    = 0
    origin_request_policy_id   = "216adef6-5c7f-47e4-b989-5492eafa07d3"
    path_pattern               = "/api/*"
    realtime_log_config_arn    = null
    response_headers_policy_id = null
    smooth_streaming           = false
    target_origin_id           = "career-support-alb-1434304583.ap-northeast-1.elb.amazonaws.com-mrpxe2ctl6d"
    trusted_key_groups         = []
    trusted_signers            = []
    viewer_protocol_policy     = "redirect-to-https"
    grpc_config {
      enabled = false
    }
  }
  ordered_cache_behavior {
    allowed_methods            = ["GET", "HEAD"]
    cache_policy_id            = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    default_ttl                = 0
    field_level_encryption_id  = null
    max_ttl                    = 0
    min_ttl                    = 0
    origin_request_policy_id   = null
    path_pattern               = "/_next/static/*"
    realtime_log_config_arn    = null
    response_headers_policy_id = null
    smooth_streaming           = false
    target_origin_id           = "s3-static-origin"
    trusted_key_groups         = []
    trusted_signers            = []
    viewer_protocol_policy     = "redirect-to-https"
    grpc_config {
      enabled = false
    }
  }
  origin {
    connection_attempts      = 3
    connection_timeout       = 10
    domain_name              = "career-support-alb-1434304583.ap-northeast-1.elb.amazonaws.com"
    origin_access_control_id = null
    origin_id                = "career-support-alb-1434304583.ap-northeast-1.elb.amazonaws.com-mrpxe2ctl6d"
    origin_path              = null
    custom_header {
      name  = "X-Origin-Verify"
      value = "65f72c5ae10ddf204b6b424c8de6782cdd5d0ce8b0de92a2d412ebc84dceab9b"
    }
    custom_origin_config {
      http_port                = 80
      https_port               = 443
      origin_keepalive_timeout = 5
      origin_protocol_policy   = "https-only"
      origin_read_timeout      = 30
      origin_ssl_protocols     = ["TLSv1.2"]
    }
  }
  origin {
    connection_attempts      = 3
    connection_timeout       = 10
    domain_name              = "career-support-static-files-460677238703.s3.ap-northeast-1.amazonaws.com"
    origin_access_control_id = "E2RLZ5D6YH2L91"
    origin_id                = "s3-static-origin"
    origin_path              = null
  }
  restrictions {
    geo_restriction {
      locations        = []
      restriction_type = "none"
    }
  }
  viewer_certificate {
    acm_certificate_arn            = "arn:aws:acm:us-east-1:460677238703:certificate/36aafc56-3048-4dad-bd70-cf9cae037d4d"
    cloudfront_default_certificate = false
    iam_certificate_id             = null
    minimum_protocol_version       = "TLSv1.2_2021"
    ssl_support_method             = "sni-only"
  }
}
