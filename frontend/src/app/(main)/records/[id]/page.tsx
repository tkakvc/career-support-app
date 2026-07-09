"use client"

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ isEditing を useState で管理するか
//   → 表示モード/編集モードの切り替えは「このページ内だけ」で完結する。
//     Zustand（グローバル）に入れる必要はなく、useState で十分。
// 【面接で説明できるようにする】なぜ form.reset() を useEffect の中で呼ぶか
//   → useForm の defaultValues はコンポーネントの初回レンダリング時に1回だけ設定される。
//     record データは API 取得後（= 非同期）に届くので、届いた時点で form.reset() を
//     呼んでフォームに値をセットし直す必要がある。
// 【AI任せでOK】Dialog の JSX 構造（DialogTrigger / DialogContent / DialogHeader など）
// ============================================================

import { useState, useEffect } from "react"
import { useRouter, useParams } from "next/navigation"
import Link from "next/link"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"

import { useLearningRecord } from "@/hooks/useLearningRecord"
import { useUpdateLearningRecord, useDeleteLearningRecord } from "@/hooks/useLearningRecordMutations"
import { useTags } from "@/hooks/useTags"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"

// ▼ 編集フォームのバリデーションスキーマ（作成ページと同じルール）
const editSchema = z.object({
  date: z.string().min(1, { message: "日付を入力してください" }),
  content: z
    .string()
    .min(1, { message: "内容を入力してください" })
    .max(2000, { message: "2000文字以内で入力してください" }),
  duration: z
    .string()
    .min(1, { message: "学習時間を入力してください" })
    .refine(
      (val) => {
        const num = parseInt(val, 10)
        return !isNaN(num) && Number.isInteger(num) && num >= 1 && num <= 1440
      },
      { message: "1〜1440の整数を入力してください" }
    ),
  tagIds: z.array(z.string()).optional(),
})

type EditFormValues = z.infer<typeof editSchema>

export default function RecordDetailPage() {
  const router = useRouter()
  const params = useParams()
  // ▼ useParams() は URL の動的セグメントを取り出す。
  // /records/abc-123 にアクセスしたとき params.id → "abc-123" になる。
  const id = params.id as string

  const { data: record, isLoading, isError } = useLearningRecord(id)
  const { data: tags } = useTags()
  const updateMutation = useUpdateLearningRecord()
  const deleteMutation = useDeleteLearningRecord()

  // ▼ isEditing: true → 編集モード / false → 表示モード
  const [isEditing, setIsEditing] = useState(false)
  // ▼ deleteDialogOpen: true → 削除確認ダイアログを表示
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  // ▼ apiError: 保存失敗時のエラーメッセージ
  const [apiError, setApiError] = useState<string | null>(null)

  const form = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: {
      date: "",
      content: "",
      duration: "",
      tagIds: [],
    },
  })

  useEffect(() => {
    // ▼ record が届いたとき（= API 取得完了後）にフォームの値をセットする。
    // record は初回レンダリング時は undefined なので if (record) で存在確認してから実行する。
    // form.reset() はフォームの全フィールドを新しい値で上書きする。
    if (record) {
      form.reset({
        date: record.date,
        content: record.content,
        duration: String(record.duration),
        // record.duration は数値（90）なので String() で文字列（"90"）に変換する。
        // フォームの duration フィールドは z.string() なので文字列で渡す必要がある。
        tagIds: record.tags.map((t) => t.id),
        // record.tags は Tag[] なので .map() で id だけの string[] に変換する。
      })
    }
  }, [record, form])

  const onSubmit = async (data: EditFormValues) => {
    setApiError(null)
    updateMutation.mutate(
      {
        id,
        body: {
          date: data.date,
          content: data.content,
          duration: parseInt(data.duration, 10),
          tagIds: data.tagIds ?? [],
        },
      },
      {
        onSuccess: () => {
          // 保存成功 → 編集モードを終了して表示モードに戻る
          setIsEditing(false)
        },
        onError: () => {
          setApiError("保存に失敗しました。しばらく経ってからお試しください")
        },
      }
    )
  }

  const handleDelete = () => {
    deleteMutation.mutate(id, {
      onSuccess: () => {
        router.push("/dashboard")
      },
    })
  }

  // ▼ ローディング中
  if (isLoading) {
    return (
      <div className="container mx-auto max-w-2xl p-6">
        <p className="text-muted-foreground">読み込み中...</p>
      </div>
    )
  }

  // ▼ エラー or データなし
  if (isError || !record) {
    return (
      <div className="container mx-auto max-w-2xl p-6 space-y-4">
        <p className="text-muted-foreground">データの取得に失敗しました。</p>
        <Link href="/dashboard" className="text-primary underline text-sm">
          一覧に戻る
        </Link>
      </div>
    )
  }

  // ▼ 編集モード
  if (isEditing) {
    return (
      <div className="container mx-auto max-w-2xl p-6 space-y-6">
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:underline">
          ← 戻る
        </Link>

        <h1 className="text-2xl font-bold">学習記録を編集</h1>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">

            <FormField
              control={form.control}
              name="date"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>日付</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="content"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>内容</FormLabel>
                  <FormControl>
                    <Textarea rows={4} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="duration"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>学習時間（分）</FormLabel>
                  <FormControl>
                    <Input type="number" min={1} max={1440} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="tagIds"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>タグ（任意）</FormLabel>
                  <div className="flex flex-wrap gap-3">
                    {tags?.map((tag) => {
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
                                field.onChange([...(field.value ?? []), tag.id])
                              } else {
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
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />

            {apiError && <p className="text-destructive text-sm">{apiError}</p>}

            <div className="flex justify-end gap-3">
              {/* ▼ キャンセル: ページ遷移せず表示モードに戻る */}
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setIsEditing(false)
                  setApiError(null)
                }}
              >
                キャンセル
              </Button>
              <Button type="submit" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? "保存中..." : "保存する"}
              </Button>
            </div>

          </form>
        </Form>
      </div>
    )
  }

  // ▼ 表示モード
  return (
    <div className="container mx-auto max-w-2xl p-6 space-y-6">
      {/* ▼ ヘッダー: 戻るリンク（左）・編集ボタン・削除ボタン（右） */}
      <div className="flex items-center justify-between">
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:underline">
          ← 戻る
        </Link>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setIsEditing(true)}>
            編集
          </Button>
          <Button variant="destructive" onClick={() => setDeleteDialogOpen(true)}>
            削除
          </Button>
        </div>
      </div>

      {/* ▼ 日付 */}
      <p className="text-muted-foreground text-sm">{record.date}</p>

      {/* ▼ タグ */}
      {record.tags.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {record.tags.map((tag) => (
            <Badge key={tag.id} variant="secondary">
              {tag.name}
            </Badge>
          ))}
        </div>
      )}

      {/* ▼ 内容（全文表示） */}
      <p className="whitespace-pre-wrap">{record.content}</p>
      {/* whitespace-pre-wrap: 改行文字（\n）を画面上の改行として表示する */}

      {/* ▼ 学習時間 */}
      <p className="text-muted-foreground text-sm">{record.duration}分</p>

      {/* ▼ 添付ファイルセクション（Step 8 で実装予定） */}
      {/* <AttachmentSection learningRecordId={record.id} /> */}

      {/* ▼ 削除確認ダイアログ */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>記録を削除しますか？</DialogTitle>
            <DialogDescription>この操作は取り消せません。</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              キャンセル
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? "削除中..." : "削除する"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
