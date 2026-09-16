"use client"

// ============================================================
// なぜ (main) グループに layout.tsx を置くか
//   → /dashboard・/records・/tags・/ai はログイン済みでないと見せたくないページ。
//     この layout.tsx でトークンを確認し、未ログインなら /login に飛ばすことで
//     各ページで個別に認証チェックを書かなくて済む。
//     全ページ共通のヘッダーナビ（各画面への動線・ログアウト）もここに置く。
//
// 【変更履歴】なぜ「サイレントリフレッシュ」で起動時にセッションを復元するか
//   → 以前は accessToken・refreshToken を localStorage に persist しており、
//     ページ起動時はそこから同期的に復元していた（_hasHydrated という待ちフラグを使っていた）。
//     リフレッシュトークンをHttpOnly Cookieに変えたことで、この方法は使えなくなった。
//     CookieはJavaScriptから読めないので、フロントは「Cookieの中身」を直接見て復元することができない。
//   → 代わりに、ページ起動時に POST /api/auth/refresh を呼ぶ。リフレッシュトークンのCookieは
//     ブラウザが自動で一緒に送ってくれるので、サーバー側で検証してもらい、新しいaccessTokenを
//     もらい直す。これを「サイレントリフレッシュ」と呼んでいる（ユーザーには見えない、静かな再認証）。
//     成功すればログイン中、失敗（Cookie無し・失効済み）すれば未ログインとして扱う。
//   → _hasCheckedAuth は、このサイレントリフレッシュの試行が完了したかどうかを表すフラグ。
//     以前の _hasHydrated と同じ役割（「まだ判定してよいタイミングではない」を表す）を、
//     「localStorage読み込み待ち」から「ネットワーク呼び出し待ち」に置き換えたもの。
//     このフラグを待たずに判定すると、まだリフレッシュ処理が終わっていないだけの
//     ログイン済みユーザーまで、一瞬 accessToken が null に見えて /login に誤って飛ばしてしまう
//     （これは以前の _hasHydrated 待ちと全く同じ理由）。
//
// なぜログアウトをフロントだけで完結させないか
//   → リフレッシュトークンはサーバー側（Redis）で失効管理している。
//     フロントの状態（Zustand）を消すだけだとRedis上のトークンは有効なまま残ってしまう。
//     POST /api/auth/logout を呼んでサーバー側も失効させてから、フロントの状態を消す。
// ============================================================

import { useEffect } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useAuthStore } from "@/store/authStore"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import api, { refreshAccessToken } from "@/lib/api"
import { UserProfile } from "@/lib/api-types"

// ▼ 全画面共通のナビゲーション項目。
const NAV_ITEMS = [
  { href: "/dashboard", label: "ダッシュボード" },
  { href: "/tags", label: "タグ管理" },
]

// ▼ layout の props は children だけ。
// children には /dashboard や /records など各ページのコンテンツが入る。
export default function MainLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  // ▼ Zustand から accessToken と、サイレントリフレッシュが終わったかのフラグを取り出す。
  // accessToken が null → 未ログイン（ただしリフレッシュ試行中は判定材料にしない）。string → ログイン済み。
  const token = useAuthStore((s) => s.accessToken)
  const hasCheckedAuth = useAuthStore((s) => s._hasCheckedAuth)
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const displayName = useAuthStore((s) => s.displayName)

  // ▼ サイレントリフレッシュ：ページ起動時（このlayoutが最初にマウントされたとき）に一度だけ実行する。
  // 依存配列が空 [] なので、/dashboard ↔ /tags のような (main) レイアウト内の画面遷移では
  // 再実行されない（Next.jsのApp Routerは、共通のlayoutを画面遷移のたびに作り直さないため）。
  useEffect(() => {
    const bootstrap = async () => {
      // ログイン画面からの遷移など、すでにaccessTokenをメモリ上に持っている場合は
      // リフレッシュを試みる必要がない（今取得したばかりの新しいトークンの方が確実に有効なため）。
      if (useAuthStore.getState().accessToken) {
        useAuthStore.getState().setHasCheckedAuth(true)
        return
      }
      try {
        // リフレッシュトークンはHttpOnly Cookieとしてブラウザが自動で送ってくれる。
        // 成功すれば新しいaccessTokenがもらえる（＝ログイン状態が生きていたということ）。
        const accessToken = await refreshAccessToken()
        // accessTokenだけではuserId・displayName・emailが分からないので、
        // 続けてプロフィールを取得してZustandを埋める（login/page.tsxと同じ手順）。
        const profile = await api
          .get<UserProfile>("/users/me", {
            headers: { Authorization: `Bearer ${accessToken}` },
          })
          .then((res) => res.data)
        useAuthStore.getState().setAuth(accessToken, profile.id, profile.displayName, profile.email)
      } catch {
        // Cookieが無い・失効済みなど → 未ログインのまま（何もしない）
      } finally {
        useAuthStore.getState().setHasCheckedAuth(true)
      }
    }
    bootstrap()
  }, [])

  // ▼ useEffect と Vue 2 の watch の対応関係
  //
  // | 実行タイミング         | React useEffect      | Vue 2 watch                    |
  // |------------------------|----------------------|--------------------------------|
  // | 初回描画後             | される               | されない（immediate: true が必要）|
  // | 依存する値が変わったとき| される               | される                         |
  // | 監視対象の書き方       | 依存配列 [token]     | watch: { token: ... }          |
  //
  // このコードを Vue 2 で書くと以下と同等：
  //   watch: {
  //     token: {
  //       immediate: true,   // ← これがないと初回の認証チェックが走らない
  //       handler(newToken) {
  //         if (!newToken) { this.$router.push("/login") }
  //       }
  //     }
  //   }
  useEffect(() => {
    // ▼ サイレントリフレッシュの試行が終わっていない間は何も判定しない（token=null がまだ
    // 「本当の未ログイン」か「リフレッシュ結果待ちで一時的にnull」なのか区別できないため）。
    if (!hasCheckedAuth) return
    // ▼ 試行が終わった上で token が null のとき（= 本当に未ログイン）だけ /login に飛ばす。
    if (!token) {
      router.push("/login")
    }
  }, [hasCheckedAuth, token, router])

  // ▼ ログアウト：サーバー側（Redis）のリフレッシュトークンを失効させてから、
  // フロントの状態（Zustand）を消してログイン画面に戻す。
  // リフレッシュトークンの値自体はHttpOnly CookieなのでJavaScriptからは見えない・渡さない。
  // ブラウザが自動でCookieを付けて /auth/logout に送ってくれるので、サーバー側で読み取って失効させる。
  const handleLogout = async () => {
    try {
      await api.post("/auth/logout")
    } finally {
      // ▼ サーバー側の失効に失敗しても（すでに切れている等）、フロントは必ずログアウトさせる
      clearAuth()
      router.push("/login")
    }
  }

  // ▼ サイレントリフレッシュ試行中、またはリダイレクト処理中は何も表示しない。
  // null を返すことでページが一瞬チラつくのを防ぐ。
  if (!hasCheckedAuth || !token) return null

  return (
    <div className="min-h-screen">
      <header className="border-b">
        <div className="container mx-auto flex items-center justify-between p-4">
          <nav className="flex gap-4 text-sm">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="text-muted-foreground hover:text-foreground hover:underline"
              >
                {item.label}
              </Link>
            ))}
          </nav>
          {/* ▼ アカウントメニュー：本体機能（nav）とアカウント操作（設定・ログアウト）を
              視覚的に分離する、SaaSで一般的な形（GitHub/Vercel/Notion等と同じ）。
              docs/settings/screen/overview.md の「ナビゲーション」参照 */}
          <DropdownMenu>
            <DropdownMenuTrigger className="rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring">
              <Avatar>
                {/* displayName の先頭1文字を丸背景に表示する。まだ復元前などで無ければ "?" */}
                <AvatarFallback>{displayName ? displayName[0] : "?"}</AvatarFallback>
              </Avatar>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem asChild>
                <Link href="/settings">設定</Link>
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleLogout}>ログアウト</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>
      <main>{children}</main>
    </div>
  )
}
