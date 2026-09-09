"use client"

// ============================================================
// なぜ (main) グループに layout.tsx を置くか
//   → /dashboard・/records・/tags・/ai はログイン済みでないと見せたくないページ。
//     この layout.tsx でトークンを確認し、未ログインなら /login に飛ばすことで
//     各ページで個別に認証チェックを書かなくて済む。
//     全ページ共通のヘッダーナビ（各画面への動線・ログアウト）もここに置く。
//
// 【躓いたポイント】なぜ _hasHydrated を待ってから判定するか
//   → Zustand persist の localStorage 復元は非同期。URLを直接開いた（他ページ経由でない）
//     瞬間は復元前で accessToken が null のままなので、待たずに判定すると
//     ログイン済みのユーザーまで /login に誤って飛ばしてしまう。
//     復元完了（_hasHydrated === true）を待ってから「token があるか」を見る。
//     詳しい仕組み（なぜ一瞬 null になるか）は store/authStore.ts の _hasHydrated のコメント参照。
//     実際に起きたバグ：/tags に直リンクした瞬間、復元前の null を見て誤って /login に飛ばされていた。
//     この layout の useEffect が「hasHydrated が true になるまでは何もしない」よう
//     早期 return しているのが直した箇所（下の useEffect の1行目）。
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
import { Button } from "@/components/ui/button"
import api from "@/lib/api"

// ▼ 全画面共通のナビゲーション項目。
const NAV_ITEMS = [
  { href: "/dashboard", label: "ダッシュボード" },
  { href: "/tags", label: "タグ管理" },
]

// ▼ layout の props は children だけ。
// children には /dashboard や /records など各ページのコンテンツが入る。
export default function MainLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  // ▼ Zustand から accessToken と、localStorage 復元が終わったかのフラグを取り出す。
  // accessToken が null → 未ログイン（ただし復元前は判定材料にしない）。string → ログイン済み。
  const token = useAuthStore((s) => s.accessToken)
  const hasHydrated = useAuthStore((s) => s._hasHydrated)
  const clearAuth = useAuthStore((s) => s.clearAuth)

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
    // ▼ 復元が終わっていない間は何も判定しない（token=null がまだ「本当の未ログイン」か
    // 「復元待ちで一時的にnull」なのか区別できないため）。
    if (!hasHydrated) return
    // ▼ 復元が終わった上で token が null のとき（= 本当に未ログイン）だけ /login に飛ばす。
    if (!token) {
      router.push("/login")
    }
  }, [hasHydrated, token, router])

  // ▼ ログアウト：サーバー側（Redis）のリフレッシュトークンを失効させてから、
  // フロントの状態（Zustand・localStorage）を消してログイン画面に戻す。
  const handleLogout = async () => {
    const refreshToken = useAuthStore.getState().refreshToken
    try {
      if (refreshToken) {
        await api.post("/auth/logout", { refreshToken })
      }
    } finally {
      // ▼ サーバー側の失効に失敗しても（すでに切れている等）、フロントは必ずログアウトさせる
      clearAuth()
      router.push("/login")
    }
  }

  // ▼ 復元待ち、またはリダイレクト処理中は何も表示しない。
  // null を返すことでページが一瞬チラつくのを防ぐ。
  if (!hasHydrated || !token) return null

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
          <Button variant="outline" size="sm" onClick={handleLogout}>
            ログアウト
          </Button>
        </div>
      </header>
      <main>{children}</main>
    </div>
  )
}
