"use client"

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ criteria を DashboardPage で持つか
//   → SearchForm が更新した検索条件を useLearningRecords に渡すには、
//     両者の親コンポーネントが criteria を管理する必要がある。
//     SearchForm（子）→ criteria → useLearningRecords（子）という流れ。
//     React はデータを上から下に流すので、共通の親である DashboardPage が持つのが正しい。
// 【面接で説明できるようにする】なぜ SearchForm の状態は useState で Zustand ではないか
//   → 検索条件は /dashboard ページを離れたらリセットして構わない一時的な値。
//     複数ページにまたがって使う情報ではないので Zustand（グローバル）に入れる必要がない。
// 【AI任せでOK】Button asChild・Link の組み合わせ方
// ============================================================

import { useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { SearchForm } from "@/components/features/dashboard/SearchForm"
import { RecordList } from "@/components/features/dashboard/RecordList"
import { useLearningRecords } from "@/hooks/useLearningRecords"
import type { SearchCriteria } from "@/lib/api-types"

export default function DashboardPage() {
  // ▼ criteria: useLearningRecords に渡す検索条件
  // SearchForm の「検索」ボタンを押したとき onSearch(criteria) で更新される。
  // 初期値は {} = 条件なし = 全件取得。
  const [criteria, setCriteria] = useState<SearchCriteria>({})

  // ▼ useLearningRecords に criteria を渡す。
  // criteria が変わると queryKey が変わり、自動で API を叩き直す。
  const { data, isLoading, isError } = useLearningRecords(criteria)

  return (
    <div className="container mx-auto max-w-3xl p-6 space-y-6">
      {/* ▼ ページヘッダー: 左に「学習記録」、右に「+ 新規作成」ボタン */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">学習記録</h1>
        {/* ▼ Button asChild + Link の組み合わせ */}
        {/* asChild は「Button の見た目を子要素（Link）に付ける」という指定。 */}
        {/* Button が <button> ではなく <a> タグとしてレンダリングされる。 */}
        <Button asChild>
          <Link href="/records/new">+ 新規作成</Link>
        </Button>
      </div>

      {/* ▼ SearchForm: 検索条件の入力 */}
      {/* onSearch に setCriteria を渡す。 */}
      {/* SearchForm 内で「検索」ボタンを押すと setCriteria(criteria) が呼ばれ、 */}
      {/* criteria が更新されて useLearningRecords が再フェッチする。 */}
      <SearchForm onSearch={setCriteria} />

      {/* ▼ RecordList: 取得結果の表示 */}
      {/* isLoading・isError・records・criteria を渡して状態ごとの表示を切り替える。 */}
      <RecordList
        records={data}
        isLoading={isLoading}
        isError={isError}
        criteria={criteria}
      />
    </div>
  )
}
