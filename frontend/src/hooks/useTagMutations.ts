// タグの作成・更新・削除フック（useLearningRecordMutations.ts と同じパターン）
import { useMutation, useQueryClient } from "@tanstack/react-query"
import type { Tag, TagCreateRequest, TagUpdateRequest } from "@/lib/api-types"
import api from "@/lib/api"

// ▼ useCreateTag: タグを1件作成する
// mutate({ name: "独自タグ" }) で呼ぶ。
export function useCreateTag() {
  const queryClient = useQueryClient()

  return useMutation<Tag, Error, TagCreateRequest>({
    mutationFn: (body) => api.post<Tag>("/tags", body).then((res) => res.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tags"] })
    },
  })
}

// ▼ useUpdateTag: タグ名を編集する
// mutate({ id: "abc", body: { name: "新しい名前" } }) で呼ぶ。
export function useUpdateTag() {
  const queryClient = useQueryClient()

  return useMutation<Tag, Error, { id: string; body: TagUpdateRequest }>({
    mutationFn: ({ id, body }) =>
      api.put<Tag>(`/tags/${id}`, body).then((res) => res.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tags"] })
    },
  })
}

// ▼ useDeleteTag: タグを削除する
// mutate("abc") で呼ぶ（id だけ渡す）。
export function useDeleteTag() {
  const queryClient = useQueryClient()

  return useMutation<void, Error, string>({
    mutationFn: (id) => api.delete(`/tags/${id}`).then(() => undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tags"] })
    },
  })
}
