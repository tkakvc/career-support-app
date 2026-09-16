// ログイン中ユーザー自身のプロフィールを取得するフック
import { useQuery } from "@tanstack/react-query"
import type { UserProfile } from "@/lib/api-types"
import api from "@/lib/api"

export function useProfile() {
  return useQuery<UserProfile>({
    queryKey: ["profile"],
    queryFn: () => api.get<UserProfile>("/users/me").then((res) => res.data),
  })
}
