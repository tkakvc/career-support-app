// プロフィール（表示名）更新・パスワード変更のフック
import { useMutation, useQueryClient } from "@tanstack/react-query"
import type {
  AuthResponse,
  PasswordUpdateResponse,
  UpdatePasswordRequest,
  UpdateProfileRequest,
  UserProfile,
} from "@/lib/api-types"
import api from "@/lib/api"
import { useAuthStore } from "@/store/authStore"

// ▼ useUpdateProfile：表示名を変更する
export function useUpdateProfile() {
  const queryClient = useQueryClient()

  return useMutation<UserProfile, Error, UpdateProfileRequest>({
    mutationFn: (body) =>
      api.patch<UserProfile>("/users/me", body).then((res) => res.data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["profile"] })
      // ヘッダーのアバター（displayNameの頭文字）にもすぐ反映させる
      useAuthStore.getState().setDisplayName(data.displayName)
    },
  })
}

// ▼ useUpdatePassword：パスワードを変更する
//
// パスワード変更に成功すると、バックエンド側でこのユーザーの全リフレッシュトークンが
// 失効する（docs/settings/api/request-response.md）。操作した本人の今のセッションも対象になる。
// 何もしないと、次にアクセストークンが切れて自動リフレッシュが走った瞬間に失敗し、
// 突然ログイン画面に飛ばされる体験になってしまう。
//
// これを避けるため、パスワード変更が成功したら「新しいパスワードでその場で再ログインし直し」、
// 新しいアクセストークンで authStore を上書きする（docs/settings/screen/overview.md の a 案を採用）。
// 新しいリフレッシュトークンはこの /auth/login レスポンスのSet-CookieでブラウザのCookieに
// 上書き保存されるため、フロント側で何かを保存する必要はない。
export function useUpdatePassword() {
  return useMutation<PasswordUpdateResponse, Error, UpdatePasswordRequest>({
    mutationFn: (body) =>
      api
        .put<PasswordUpdateResponse>("/users/me/password", body)
        .then((res) => res.data),
    // onSuccess の第1引数はサーバーの戻り値（PasswordUpdateResponse）、
    // 第2引数の variables は mutate() に渡した元の引数（UpdatePasswordRequest、41行目の body）。
    // ここでは戻り値の中身を使わないので _data として受け取る（先頭の _ は「受け取るが使わない」の慣習的な印）。
    // 再ログインに新しいパスワードの文字列が要るので、variables.newPassword だけを下で使う。
    onSuccess: async (_data, variables) => {
      const { email, userId, displayName } = useAuthStore.getState()
      // email は login 成功時に必ずセットされているので、ここでは基本 null にならない
      if (!email) return

      const loginRes = await api.post<AuthResponse>("/auth/login", {
        email,
        password: variables.newPassword,
      })
      const { accessToken } = loginRes.data
      useAuthStore
        .getState()
        .setAuth(accessToken, userId ?? "", displayName ?? "", email)
    },
  })
}
