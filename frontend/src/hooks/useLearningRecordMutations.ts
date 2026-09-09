// 学習記録の更新・削除フック
import { useMutation, useQueryClient } from "@tanstack/react-query"
import type { LearningRecord, LearningRecordUpdateRequest } from "@/lib/api-types"
import api from "@/lib/api"

// ▼ useUpdateLearningRecord: 1件の学習記録を更新するフック
// mutate({ id: "abc", body: { date: "...", content: "...", duration: 90, tagIds: [] } }) で呼ぶ。
export function useUpdateLearningRecord() {
  const queryClient = useQueryClient()

  return useMutation<
    LearningRecord,
    Error,
    // ▼ mutate() に渡す引数の型: id と更新内容（body）をセットで渡す
    { id: string; body: LearningRecordUpdateRequest }
  >({
    mutationFn: ({ id, body }) =>
      api.put<LearningRecord>(`/learning-records/${id}`, body).then((res) => res.data),

    onSuccess: (_, { id }) => {
      // ▼ 成功後に2つのキャッシュを無効化する
      // 1件取得のキャッシュ（["learning-records", id]）→ 詳細ページの表示を最新化する
      queryClient.invalidateQueries({ queryKey: ["learning-records", id] })
      // 一覧のキャッシュ（["learning-records"]）→ 一覧ページの表示を最新化する
      queryClient.invalidateQueries({ queryKey: ["learning-records"] })
      // ▼ onSuccess の第2引数 _ は「mutate() に渡した引数（= { id, body }）」。
      // data（第1引数）は使わないので _ で省略し、id だけ取り出している。
    },
  })
}

// ▼ useDeleteLearningRecord: 1件の学習記録を削除するフック
// mutate("abc") で呼ぶ（id だけ渡す）。
export function useDeleteLearningRecord() {
  const queryClient = useQueryClient()

  return useMutation<void, Error, string>({
    // 型引数の3つ目が string → mutate() には id（string）を渡す
    mutationFn: (id) =>
      api.delete(`/learning-records/${id}`).then(() => undefined),
    // DELETE は成功してもレスポンスボディがないので .then(() => undefined) で void を返す

    onSuccess: () => {
      // 削除後は id が存在しなくなるので、一覧キャッシュだけ無効化する。
      // 詳細ページには戻らないため ["learning-records", id] の無効化は不要。
      queryClient.invalidateQueries({ queryKey: ["learning-records"] })
    },
  })
}
