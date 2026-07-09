// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ状態ごとに表示を分けるか
//   → isLoading・isError・データなし・データあり の4つは全て別の状態。
//     まとめて書くと条件分岐が複雑になるので、上から順に early return で捌く。
//     「この条件に当てはまったらここで return して終わり」という読み方ができる。
// 【AI任せでOK】Skeleton・Link の JSX 構造と Tailwind クラス名
// ============================================================

import Link from "next/link"
import { Skeleton } from "@/components/ui/skeleton"
import { RecordCard } from "./RecordCard"
import type { LearningRecord, SearchCriteria } from "@/lib/api-types"

type Props = {
  records: LearningRecord[] | undefined
  isLoading: boolean
  isError: boolean
  // ▼ criteria を受け取る理由:
  // 「0件」のとき、検索条件があるかないかで表示するメッセージが変わる。
  // 検索条件なし → 「記録がありません」
  // 検索条件あり → 「記録が見つかりませんでした」
  criteria: SearchCriteria
}

// ▼ criteria に空でない値が1つでもあるか確認する関数
// Object.values({ keyword: "Java", from: "" }) → ["Java", ""]
// .some((v) => v !== undefined && v !== "") → "Java" が空でないので true を返す
function hasActiveCriteria(criteria: SearchCriteria): boolean {
  return Object.values(criteria).some((v) => v !== undefined && v !== "")
}

export function RecordList({ records, isLoading, isError, criteria }: Props) {
  // ▼ 状態1: エラー
  // isError が true のとき（APIが失敗したとき）に真っ先に表示する。
  if (isError) {
    return (
      <p className="text-center text-muted-foreground py-12">
        データの取得に失敗しました。ページを再読み込みしてください
      </p>
    )
  }

  // ▼ 状態2: ローディング中
  // Skeleton を3枚並べて「読み込み中」を伝える。
  // RecordCard と同じ高さ（h-24）にすることでレイアウトのずれを防ぐ。
  if (isLoading) {
    return (
      <div className="space-y-3">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-24 w-full rounded-xl" />
        ))}
      </div>
    )
  }

  // ▼ 状態3: 0件
  if (!records || records.length === 0) {
    // 検索条件がある場合: 「見つかりませんでした」
    if (hasActiveCriteria(criteria)) {
      return (
        <p className="text-center text-muted-foreground py-12">
          記録が見つかりませんでした
        </p>
      )
    }
    // 検索条件がない場合（初回・全件取得で0件）: 「記録がありません」+ 追加リンク
    return (
      <div className="text-center py-12 space-y-3">
        <p className="text-muted-foreground">記録がありません</p>
        <Link href="/records/new" className="text-primary underline text-sm">
          最初の記録を追加する →
        </Link>
      </div>
    )
  }

  // ▼ 状態4: データあり
  // records.map() で各 LearningRecord を RecordCard に変換して縦並びにする。
  // key={record.id} → React がどのカードが変わったか追跡するための一意なキー。
  return (
    <div className="space-y-3">
      {records.map((record) => (
        <RecordCard key={record.id} record={record} />
      ))}
    </div>
  )
}
