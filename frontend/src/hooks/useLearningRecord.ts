// 学習記録を1件取得するフック
import { useQuery } from "@tanstack/react-query"
import type { LearningRecord } from "@/lib/api-types"
import api from "@/lib/api"

export function useLearningRecord(id: string) {
  return useQuery<LearningRecord>({
    queryKey: ["learning-records", id],
    // queryKey に id を含める理由:
    // id が "abc" のとき ["learning-records", "abc"] というキーになる。
    // id が "xyz" のとき ["learning-records", "xyz"] という別のキーになる。
    // それぞれ別のキャッシュとして管理されるので、
    // /records/abc と /records/xyz を行き来しても両方キャッシュが効く。
    queryFn: () =>
      api.get<LearningRecord>(`/learning-records/${id}`).then((res) => res.data),
  })
}
