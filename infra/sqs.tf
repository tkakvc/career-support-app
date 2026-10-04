# 参考資料生成（AiService.generateResult）のOpenAI呼び出しを非同期化するためのキュー
# 設計は docs/ai/implementation.md 参照

resource "aws_sqs_queue" "ai_jobs_dlq" {
  name = "career-support-ai-jobs-dlq"
}

resource "aws_sqs_queue" "ai_jobs" {
  name                       = "career-support-ai-jobs"
  visibility_timeout_seconds = 90 # AiJobWorker側のOpenAI呼び出し（最大62秒）より長く取る

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ai_jobs_dlq.arn
    maxReceiveCount     = 3
  })
}
