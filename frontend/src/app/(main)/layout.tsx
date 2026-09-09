"use client"

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ (main) グループに layout.tsx を置くか
//   → /dashboard・/records・/tags・/ai はログイン済みでないと見せたくないページ。
//     この layout.tsx でトークンを確認し、未ログインなら /login に飛ばすことで
//     各ページで個別に認証チェックを書かなくて済む。
// 【面接で説明できるようにする】なぜ _hasHydrated を待ってから判定するか
//   → Zustand persist の localStorage 復元は非同期。URLを直接開いた（他ページ経由でない）
//     瞬間は復元前で accessToken が null のままなので、待たずに判定すると
//     ログイン済みのユーザーまで /login に誤って飛ばしてしまう。
//     復元完了（_hasHydrated === true）を待ってから「token があるか」を見る。
//     詳しい仕組み（なぜ一瞬 null になるか）は store/authStore.ts の _hasHydrated のコメント参照。
//     実際に起きたバグ：/tags に直リンクした瞬間、復元前の null を見て誤って /login に飛ばされていた。
//     この layout の useEffect が「hasHydrated が true になるまでは何もしない」よう
//     早期 return しているのが直した箇所（下の useEffect の1行目）。
// 【AI任せでOK】useEffect と router.push の構文
// ============================================================

import { useEffect } from "react"
import { useRouter } from "next/navigation"
import { useAuthStore } from "@/store/authStore"

// ▼ layout の props は children だけ。
// children には /dashboard や /records など各ページのコンテンツが入る。
export default function MainLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  // ▼ Zustand から accessToken と、localStorage 復元が終わったかのフラグを取り出す。
  // accessToken が null → 未ログイン（ただし復元前は判定材料にしない）。string → ログイン済み。
  const token = useAuthStore((s) => s.accessToken)
  const hasHydrated = useAuthStore((s) => s._hasHydrated)

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

  // ▼ 復元待ち、またはリダイレクト処理中は何も表示しない。
  // null を返すことでページが一瞬チラつくのを防ぐ。
  if (!hasHydrated || !token) return null

  return <>{children}</>
}
