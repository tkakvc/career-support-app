// AI参考資料生成を呼ぶフック（useTagMutations.ts と同じパターン）
// 画面表示だけで自動的にOpenAI/Web検索のコストを発生させないため、useQuery ではなく
// ボタン押下で発火する useMutation で扱う（docs/ai/screen/overview.md 参照）
import { useMutation, useQuery } from "@tanstack/react-query"
import type {
  AiJobAcceptedResponse,
  AiJobResponse,
  AiReferenceListResponse,
  ReferenceRequest,
} from "@/lib/api-types"
import api from "@/lib/api"

// ▼ useGenerateReference: 参考資料の生成をリクエストする。mutate({ interest: "..." }) で呼ぶ（interestは任意）
// 202 + jobId（非同期で生成中）、または200 + message（学習記録0件などの即時応答）が返る
export function useGenerateReference() {
  return useMutation<AiJobAcceptedResponse, Error, ReferenceRequest>({
    mutationFn: (body) => api.post<AiJobAcceptedResponse>("/ai/references", body).then((res) => res.data),
  })
}

// ▼ useAiJob: jobIdの状態・結果をポーリングする。jobIdがnullの間は取得しない
// ここはuseMutationではなくuseQueryを使う。useGenerateReferenceとは逆で、「ジョブができた後は
// 自動的に・繰り返し確認したい」という性質だから。副作用（コスト）が発生するのはenqueue側だけで、
// ポーリング自体はOpenAI/Web検索を呼ばずDBを読むだけなので、自動発火して問題ない。
export function useAiJob(jobId: string | null) {
  return useQuery<AiJobResponse, Error>({
    queryKey: ["ai-job", jobId],
    queryFn: () => api.get<AiJobResponse>(`/ai/jobs/${jobId}`).then((res) => res.data),
    enabled: jobId !== null,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (status === "DONE" || status === "FAILED") return false // 完了したら止める
      return 2000 // それ以外は2秒ごとに再取得
    },
  })
}

// ▼ useAiReferences: 保存済み参考資料の一覧を取得する。tagIdを渡すとそのタグで絞り込む
export function useAiReferences(tagId: string | null) {
  return useQuery<AiReferenceListResponse, Error>({
    queryKey: ["ai-references", tagId],
    queryFn: () =>
      api
        .get<AiReferenceListResponse>("/ai/references", { params: tagId ? { tag: tagId } : undefined })
        .then((res) => res.data),
  })
}
