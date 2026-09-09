// タグ一覧を取得するフック
import { useQuery } from "@tanstack/react-query"
import type { Tag } from "@/lib/api-types"
import api from "@/lib/api"

export function useTags() {
  return useQuery<Tag[]>({
    queryKey: ["tags"],
    // queryKey が ["tags"] → タグ一覧のキャッシュを識別するキー。
    // useCreateTag などで invalidateQueries({ queryKey: ["tags"] }) を呼ぶと
    // このキャッシュが無効になり、自動で GET /api/tags を叩き直す。
    queryFn: () => api.get<Tag[]>("/tags").then((res) => res.data),
  })
}
