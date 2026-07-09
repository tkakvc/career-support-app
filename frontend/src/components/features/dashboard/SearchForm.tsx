"use client"

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ SearchForm の状態を useState で管理するか
//   → 検索フォームの入力値はこのコンポーネント内だけで使う一時的な値。
//     「検索」ボタンを押したときだけ親（DashboardPage）に渡せばいいので
//     Zustand（グローバル状態）に入れる必要はない。
//     useState で十分。
// 【面接で説明できるようにする】なぜ criteria に空文字を含めないか
//   → API に "keyword=&tag=" のような空のクエリパラメータを送ると、
//     バックエンドの挙動が実装依存になる（空文字で全件一致になるかもしれない）。
//     空文字のフィールドは criteria に含めないことで「条件なし」を明確にする。
// 【AI任せでOK】Input・Button の className
// ============================================================

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import type { SearchCriteria } from "@/lib/api-types"

type Props = {
  // ▼ onSearch: 親（DashboardPage）から渡される関数。
  // SearchForm が「検索」「リセット」を押したとき、この関数で親の criteria を更新する。
  onSearch: (criteria: SearchCriteria) => void
}

// ▼ フォームの入力値を管理する型
// SearchCriteria と同じキーだが、すべて string（空文字あり）で持つ。
// SearchCriteria は空文字を含まない設計なので、型を分けている。
type FormState = {
  keyword: string
  tag: string
  from: string
  to: string
}

// ▼ フォームの初期値（全フィールドが空文字）
// リセット時にも使うので定数として切り出している。
const emptyForm: FormState = { keyword: "", tag: "", from: "", to: "" }

export function SearchForm({ onSearch }: Props) {
  // ▼ useState のざっくりしたイメージ（超簡略化）
  //
  //   function useState(initialValue) {
  //     let state = initialValue
  //
  //     function setState(newValue) {
  //       state = newValue
  //       画面を再描画する()   ← 一番重要なのはここ
  //     }
  //
  //     return [state, setState]
  //   }
  //
  // 普通の変数に代入しても画面は変わらない：
  //   let keyword = ""
  //   keyword = "Java"  → 画面は変わらない
  //
  // useState が返す setForm を使うと画面が変わる：
  //   setForm({ keyword: "Java", ... })  → React が再描画して画面に "Java" が表示される
  //
  // 「値を保持する＋書き換えたら画面を更新する」という2つをセットでやってくれる関数。

  // ▼ form: フォームの現在の入力値
  // { keyword: "Java", tag: "", from: "2026-06-01", to: "" } のような値が入る。
  const [form, setForm] = useState<FormState>(emptyForm)

  function handleSearch() {
    // ▼ 空文字のフィールドを除いた criteria を作る
    // form が { keyword: "Java", tag: "", from: "", to: "" } のとき
    // criteria は { keyword: "Java" } になる。
    const criteria: SearchCriteria = {}
    if (form.keyword) criteria.keyword = form.keyword
    if (form.tag) criteria.tag = form.tag
    if (form.from) criteria.from = form.from
    if (form.to) criteria.to = form.to
    onSearch(criteria)
  }

  function handleReset() {
    // ▼ フォームを初期値（全空文字）に戻す
    setForm(emptyForm)
    // ▼ 親の criteria も {} に戻す → 全件取得に切り替わる
    onSearch({})
  }

  return (
    <div className="flex flex-wrap gap-2 items-end">
      {/* ▼ キーワード入力 */}
      {/* onChange はブラウザの仕様で「input の値が変わったとき」に自動で呼ばれる。 */}
      {/* 呼ばれるとき (e) に「イベントオブジェクト」が渡ってくる。これはブラウザが渡してくる。 */}
      {/* e.target  → イベントが起きた <input> 要素そのもの */}
      {/* e.target.value → その <input> の今の入力値（例: "Java"） */}
      {/*  */}
      {/* setForm((prev) => ...) の (prev) は React の useState の仕様。 */}
      {/* setForm に関数を渡すと、React が「今の form の値」を prev に入れてその関数を呼ぶ。 */}
      {/* このプロジェクトの設定ではなく、React が最初からそう動くように作っている。 */}
      {/*  */}
      {/* { ...prev, keyword: e.target.value } のスプレッド構文について: */}
      {/* 同じキーが2つになるように見えるが、後に書いた方が上書きされる。 */}
      {/* prev = { keyword: "", tag: "", from: "", to: "" } のとき */}
      {/* { ...prev, keyword: "Java" } → { keyword: "Java", tag: "", from: "", to: "" } */}
      {/*                                               ↑ "" が "Java" に上書きされた */}
      <Input
        placeholder="キーワード"
        value={form.keyword}
        onChange={(e) => setForm((prev) => ({ ...prev, keyword: e.target.value }))}
        className="w-40"
      />

      {/* ▼ タグ名入力 */}
      <Input
        placeholder="タグ名"
        value={form.tag}
        onChange={(e) => setForm((prev) => ({ ...prev, tag: e.target.value }))}
        className="w-32"
      />

      {/* ▼ 開始日・終了日 */}
      {/* shadcn/ui には DatePicker があるが、<input type="date"> でシンプルに実装する。 */}
      {/* className で shadcn/ui の Input と見た目を揃えている。 */}
      <input
        type="date"
        value={form.from}
        onChange={(e) => setForm((prev) => ({ ...prev, from: e.target.value }))}
        className="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs"
      />
      <input
        type="date"
        value={form.to}
        onChange={(e) => setForm((prev) => ({ ...prev, to: e.target.value }))}
        className="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs"
      />

      {/* ▼ 検索ボタン: クリックで handleSearch を呼ぶ */}
      <Button onClick={handleSearch}>検索</Button>

      {/* ▼ リセットボタン: クリックで handleReset を呼ぶ */}
      <Button variant="outline" onClick={handleReset}>リセット</Button>
    </div>
  )
}
