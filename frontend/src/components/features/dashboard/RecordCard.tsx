"use client"

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ useRouter で遷移するか
//   → カード全体をクリック可能にしたいので <a> タグではなく onClick を使う。
//     onClick の中で router.push() を呼ぶと Next.js のクライアントサイド遷移になる。
//     <a href="..."> だとフルページリロードになってしまう。
// 【AI任せでOK】Card・CardContent の JSX 構造と Tailwind クラス名
// ============================================================

import { useRouter } from "next/navigation"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import type { LearningRecord } from "@/lib/api-types"

// ▼ Props の型定義
// RecordCard が受け取る値は「LearningRecord 1件分」だけ。
type Props = {
  record: LearningRecord
}

export function RecordCard({ record }: Props) {
  const router = useRouter()

  return (
    // ▼ Card 全体をクリック可能にする
    // cursor-pointer → マウスカーソルを手の形にする（クリックできると伝える）
    // hover:shadow-md → ホバー時に影を付けてインタラクティブ感を出す
    // transition-shadow → 影の変化を滑らかにする
    <Card
      className="cursor-pointer hover:shadow-md transition-shadow"
      onClick={() => router.push(`/records/${record.id}`)}
    >
      <CardContent className="space-y-2">
        {/* ▼ 上段: date（左） + tags（右） */}
        <div className="flex items-start justify-between gap-2">
          {/* date は "2026-06-20" のままそのまま表示（フォーマット変換なし） */}
          <span className="text-sm text-muted-foreground shrink-0">{record.date}</span>
          {/* ▼ tags を Badge で横並び表示 */}
          {/* record.tags が空配列のときは何も表示しない */}
          <div className="flex gap-1 flex-wrap justify-end">
            {record.tags.map((tag) => (
              <Badge key={tag.id} variant="secondary">
                {tag.name}
              </Badge>
            ))}
          </div>
        </div>

        {/* ▼ 中段: content（最大2行で省略） */}
        {/* line-clamp-2 は CSS の -webkit-line-clamp: 2 を適用する Tailwind クラス。 */}
        {/* 3行目以降は「...」で省略される。 */}
        <p className="line-clamp-2 text-sm">{record.content}</p>

        {/* ▼ 下段: duration（右寄せ、「90分」形式） */}
        {/* record.duration は数値（例: 90）なので、「分」を文字列で付けて表示する。 */}
        <p className="text-sm text-muted-foreground text-right">{record.duration}分</p>
      </CardContent>
    </Card>
  )
}
