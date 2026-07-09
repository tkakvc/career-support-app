"use client"

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ (main) グループに layout.tsx を置くか
//   → /dashboard・/records・/tags・/ai はログイン済みでないと見せたくないページ。
//     この layout.tsx でトークンを確認し、未ログインなら /login に飛ばすことで
//     各ページで個別に認証チェックを書かなくて済む。
// 【AI任せでOK】useEffect と router.push の構文
// ============================================================

import { useEffect } from "react"
import { useRouter } from "next/navigation"
import { useAuthStore } from "@/store/authStore"

// ▼ layout の props は children だけ。
// children には /dashboard や /records など各ページのコンテンツが入る。
export default function MainLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  // ▼ Zustand から token だけを取り出す。
  // token が null → 未ログイン。string → ログイン済み。
  const token = useAuthStore((s) => s.token)

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
    // ▼ token が null のとき（= 未ログイン）だけ /login に飛ばす。
    // useEffect の中で呼ぶことで、初回レンダリングの後に実行される。
    if (!token) {
      router.push("/login")
    }
  }, [token, router])

  // ▼ token が null のとき（リダイレクト処理中）は何も表示しない。
  // null を返すことでページが一瞬チラつくのを防ぐ。
  if (!token) return null

  return <>{children}</>
}
