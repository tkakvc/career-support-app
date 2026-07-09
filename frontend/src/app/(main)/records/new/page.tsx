"use client"

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ duration に z.coerce.number() を使うか
//   → <input type="number"> の値は HTML 的には文字列（"90" など）で渡ってくる。
//     z.string() のままでは数値として検証できないので z.coerce.number() で
//     "90" → 90 に変換してから検証する。coerce は「強制変換する」という意味。
// 【面接で説明できるようにする】なぜ tagIds を React Hook Form で管理するか
//   → タグのチェックボックスも「フォームの入力値」なので React Hook Form に任せる。
//     tagIds は string[] 型（選択されたタグの id の配列）になる。
// 【AI任せでOK】Form / FormField / FormItem / FormLabel / FormControl / FormMessage の構造
// ============================================================

import { useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"

import { useCreateLearningRecord } from "@/hooks/useCreateLearningRecord"
import { useTags } from "@/hooks/useTags"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"

// ▼ Zod バリデーションスキーマ
const createSchema = z.object({
  date: z.string().min(1, { message: "日付を入力してください" }),

  content: z
    .string()
    .min(1, { message: "内容を入力してください" })
    .max(2000, { message: "2000文字以内で入力してください" }),

  // ▼ duration を z.string() で受け取る理由:
  // <input type="number"> の値は HTML 的には文字列（"90" など）で渡ってくる。
  // z.coerce.number() を使うと Zod v4 + React Hook Form の型が合わなくなるため、
  // フォームでは z.string() で受け取り、onSubmit の中で parseInt() で数値に変換する。
  duration: z
    .string()
    .min(1, { message: "学習時間を入力してください" })
    // ▼ .refine() は「.min() などの既製チェックでは書けない、自分で書いた判定ロジックを追加するメソッド」。
    // 第1引数: (val) => true か false を返す関数。true なら通過、false ならエラー。
    // 第2引数: false のときに表示するエラーメッセージ。
    .refine(
      (val) => {
        const num = parseInt(val, 10)
        // parseInt("90") → 90（整数）
        // parseInt("90.5") → 90（小数は切り捨て。後の isInteger で弾く）
        // parseInt("abc") → NaN（数値でない文字列）
        return !isNaN(num) && Number.isInteger(num) && num >= 1 && num <= 1440
      },
      { message: "1〜1440の整数を入力してください" }
    ),

  // ▼ tagIds は任意（optional）なので undefined でも通る。
  // 選択されたタグの id（string）の配列。チェックなしなら []。
  tagIds: z.array(z.string()).optional(),
})

type CreateFormValues = z.infer<typeof createSchema>
// duration は string 型（"90" など）。数値への変換は onSubmit で行う。

export default function NewRecordPage() {
  const router = useRouter()
  const mutation = useCreateLearningRecord()
  const { data: tags } = useTags()
  // ▼ tags: Tag[] | undefined（取得中は undefined）
  // タグ一覧のチェックボックスに使う。

  // ▼ apiError: API が失敗したときのエラーメッセージ
  const [apiError, setApiError] = useState<string | null>(null)

  const form = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      date: "",
      content: "",
      duration: "",
      // duration は z.string() なので初期値も空文字にする
      tagIds: [],
    },
  })

  const onSubmit = async (data: CreateFormValues) => {
    setApiError(null)

    mutation.mutate(
      {
        date: data.date,
        content: data.content,
        duration: parseInt(data.duration, 10),
        // data.duration は "90" などの文字列なので、parseInt() で数値に変換する。
        // parseInt("90", 10) → 90（第2引数の 10 は「10進数で解釈する」という意味）
        tagIds: data.tagIds ?? [],
        // ?? [] は「tagIds が undefined のとき空配列を使う」という意味。
        // nullish coalescing（ヌリッシュ合体）演算子。
      },
      {
        onSuccess: () => {
          // 保存成功 → /dashboard にリダイレクト
          router.push("/dashboard")
        },
        onError: () => {
          setApiError("保存に失敗しました。しばらく経ってからお試しください")
        },
      }
    )
  }

  return (
    <div className="container mx-auto max-w-2xl p-6 space-y-6">
      {/* ▼ 戻るリンク */}
      <Link href="/dashboard" className="text-sm text-muted-foreground hover:underline">
        ← 戻る
      </Link>

      <h1 className="text-2xl font-bold">学習記録を追加</h1>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">

          {/* ▼ 日付 */}
          <FormField
            control={form.control}
            name="date"
            render={({ field }) => (
              <FormItem>
                <FormLabel>日付</FormLabel>
                <FormControl>
                  {/* type="date" で日付ピッカーを表示する */}
                  <Input type="date" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* ▼ 内容 */}
          <FormField
            control={form.control}
            name="content"
            render={({ field }) => (
              <FormItem>
                <FormLabel>内容</FormLabel>
                <FormControl>
                  {/* rows={4} で高さを4行分にする */}
                  <Textarea placeholder="学習内容を入力してください" rows={4} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* ▼ 学習時間 */}
          <FormField
            control={form.control}
            name="duration"
            render={({ field }) => (
              <FormItem>
                <FormLabel>学習時間（分）</FormLabel>
                <FormControl>
                  {/* type="number" で数値入力。min/max はブラウザのUIヒント（バリデーションは Zod が行う） */}
                  <Input
                    type="number"
                    min={1}
                    max={1440}
                    placeholder="例: 90"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* ▼ タグ選択（チェックボックス） */}
          <FormField
            control={form.control}
            name="tagIds"
            render={({ field }) => (
              <FormItem>
                <FormLabel>タグ（任意）</FormLabel>
                <div className="flex flex-wrap gap-3">
                  {tags?.map((tag) => {
                    // field.value は選択中の tagIds の配列（例: ["uuid-1", "uuid-2"]）
                    const checked = field.value?.includes(tag.id) ?? false
                    return (
                      <label
                        key={tag.id}
                        className="flex items-center gap-1.5 cursor-pointer text-sm"
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => {
                            if (e.target.checked) {
                              // チェックした → id を配列に追加
                              field.onChange([...(field.value ?? []), tag.id])
                            } else {
                              // チェックを外した → id を配列から除外
                              field.onChange(
                                (field.value ?? []).filter((id) => id !== tag.id)
                              )
                            }
                          }}
                        />
                        {tag.name}
                      </label>
                    )
                  })}
                  {/* タグが0件のとき */}
                  {tags?.length === 0 && (
                    <p className="text-sm text-muted-foreground">タグがありません</p>
                  )}
                </div>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* ▼ APIエラー（保存するボタンの上） */}
          {apiError && <p className="text-destructive text-sm">{apiError}</p>}

          {/* ▼ キャンセル・保存するボタン（右寄せ） */}
          <div className="flex justify-end gap-3">
            <Button variant="outline" asChild>
              <Link href="/dashboard">キャンセル</Link>
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "保存中..." : "保存する"}
            </Button>
          </div>

        </form>
      </Form>
    </div>
  )
}
