"use client"

// ============================================================
// 【このファイル全体の方針】
// 設計は docs/tags/screen/overview.md、対応ユースケースは docs/tags/api/usecase.md UC-06〜09。
//
// 【面接で説明できるようにする】なぜ default タグには編集・削除ボタンを出さないか
//   → PUT/DELETE /api/tags/{id} は default タグを指定すると 403 を返す（バックエンドの権限チェック）。
//     「押したら失敗する操作」をそもそもUIに出さない方が、エラー処理も減り利用者も迷わない。
// 【面接で説明できるようにする】なぜソートをフロント側（Array.sort）でやるか
//   → GET /api/tags は全件を一度に返す設計（ページングなし・件数も少ない想定）。
//     サーバーにソート済みで返させるためにクエリパラメータを増やすより、
//     取得済みの配列をその場でソートする方がAPIをシンプルに保てる。
// 【AI任せでOK】Dialog / Skeleton など shadcn/ui コンポーネントの組み方
// ============================================================

import { useMemo, useState } from "react"
import { ArrowUp, ArrowDown, ArrowUpDown } from "lucide-react"

import { useTags } from "@/hooks/useTags"
import { useCreateTag, useUpdateTag, useDeleteTag } from "@/hooks/useTagMutations"
import type { Tag } from "@/lib/api-types"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

// ▼ ソート対象の列。overview.md の「タグ名／種別／作成日」に対応する。
type SortKey = "name" | "type" | "createdAt"
type SortDirection = "asc" | "desc"

// ▼ APIエラー（axios）から HTTP ステータスを取り出すヘルパー。
// login/page.tsx と同じ判定の仕方（error.response.status を見る）。
function getErrorStatus(error: unknown): number | undefined {
  if (error && typeof error === "object" && "response" in error) {
    return (error as { response?: { status?: number } }).response?.status
  }
  return undefined
}

export default function TagsPage() {
  const { data: tags, isLoading } = useTags()
  const createTag = useCreateTag()
  const updateTag = useUpdateTag()
  const deleteTag = useDeleteTag()

  // ▼ 新規作成フォームの状態。1フィールドだけなので react-hook-form は使わず useState で足りる。
  const [newName, setNewName] = useState("")
  const [createError, setCreateError] = useState<string | null>(null)

  // ▼ インライン編集中のタグID（null なら誰も編集していない）と、編集中の入力値。
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState("")
  const [editError, setEditError] = useState<string | null>(null)

  // ▼ 削除確認ダイアログの対象タグ（null なら非表示）。
  const [deleteTarget, setDeleteTarget] = useState<Tag | null>(null)

  // ▼ 列ソートの状態。初期値は「作成日・昇順」（overview.md の初期表示に合わせる）。
  const [sort, setSort] = useState<{ key: SortKey; direction: SortDirection }>({
    key: "createdAt",
    direction: "asc",
  })

  // ▼ 列ヘッダーをクリックしたときの処理。
  // 同じ列を再度クリック→昇順/降順を反転、別の列をクリック→その列の昇順から始める。
  const handleSort = (key: SortKey) => {
    setSort((prev) =>
      prev.key === key
        ? { key, direction: prev.direction === "asc" ? "desc" : "asc" }
        : { key, direction: "asc" }
    )
  }

  // ▼ ソート済みの一覧。tags や sort が変わったときだけ計算し直す。
  const sortedTags = useMemo(() => {
    if (!tags) return []
    // [...tags] でコピーしてから sort する（元の配列・キャッシュを直接書き換えない）
    return [...tags].sort((a, b) => {
      const aValue = a[sort.key]
      const bValue = b[sort.key]
      // localeCompare: 文字列の大小比較（日本語も含めて辞書順に近い比較ができる）
      const compared = aValue.localeCompare(bValue)
      return sort.direction === "asc" ? compared : -compared
    })
  }, [tags, sort])

  // ▼ 列ヘッダーに出すソートアイコン。ソート対象でない列は薄い上下矢印、
  // 対象の列は向きに応じた矢印を表示する。
  const sortIcon = (key: SortKey) => {
    if (sort.key !== key) return <ArrowUpDown className="size-3.5 text-muted-foreground/50" />
    return sort.direction === "asc" ? (
      <ArrowUp className="size-3.5" />
    ) : (
      <ArrowDown className="size-3.5" />
    )
  }

  // ▼ 作成フォームの送信
  const handleCreate = () => {
    setCreateError(null)
    createTag.mutate(
      { name: newName },
      {
        onSuccess: () => setNewName(""),
        onError: (error) => {
          setCreateError(
            getErrorStatus(error) === 409
              ? "同じ名前のタグがすでにあります"
              : "タグの作成に失敗しました"
          )
        },
      }
    )
  }

  // ▼ 編集開始：対象タグのIDと現在の名前をセットし、行をインライン編集モードにする
  const startEditing = (tag: Tag) => {
    setEditingId(tag.id)
    setEditingName(tag.name)
    setEditError(null)
  }

  const cancelEditing = () => {
    setEditingId(null)
    setEditingName("")
    setEditError(null)
  }

  const handleUpdate = (id: string) => {
    setEditError(null)
    updateTag.mutate(
      { id, body: { name: editingName } },
      {
        onSuccess: () => {
          setEditingId(null)
          setEditingName("")
        },
        onError: (error) => {
          const status = getErrorStatus(error)
          setEditError(
            status === 409
              ? "同じ名前のタグがすでにあります"
              : status === 403
                ? "このタグは編集できません"
                : "タグの更新に失敗しました"
          )
        },
      }
    )
  }

  const handleDelete = () => {
    if (!deleteTarget) return
    deleteTag.mutate(deleteTarget.id, {
      onSuccess: () => setDeleteTarget(null),
    })
  }

  const userTags = sortedTags.filter((t) => t.type === "user")

  return (
    <div className="container mx-auto max-w-2xl p-6 space-y-6">
      <h1 className="text-2xl font-bold">タグ管理</h1>

      {/* ▼ 新規作成：一覧の上に1行フォーム（別画面・別モーダルにしない） */}
      <div className="space-y-1">
        <div className="flex gap-2">
          <Input
            placeholder="新しいタグ名"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            maxLength={50}
          />
          <Button
            onClick={handleCreate}
            disabled={newName.trim().length === 0 || createTag.isPending}
          >
            {createTag.isPending ? "追加中..." : "追加"}
          </Button>
        </div>
        {/* 50文字超は maxLength で入力自体を止めているため、カウンタで残り文字数だけ示す */}
        <p className="text-xs text-muted-foreground">{newName.length}/50文字</p>
        {createError && <p className="text-sm text-destructive">{createError}</p>}
      </div>

      {/* ▼ 一覧（テーブル形式・列ヘッダークリックでソート） */}
      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-8 w-full" />
        </div>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-muted-foreground">
              <th className="py-2">
                <button
                  className="flex items-center gap-1 hover:text-foreground"
                  onClick={() => handleSort("name")}
                >
                  タグ名 {sortIcon("name")}
                </button>
              </th>
              <th className="py-2">
                <button
                  className="flex items-center gap-1 hover:text-foreground"
                  onClick={() => handleSort("type")}
                >
                  種別 {sortIcon("type")}
                </button>
              </th>
              <th className="py-2">
                <button
                  className="flex items-center gap-1 hover:text-foreground"
                  onClick={() => handleSort("createdAt")}
                >
                  作成日 {sortIcon("createdAt")}
                </button>
              </th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {sortedTags.map((tag) => (
              <tr key={tag.id} className="border-b last:border-0">
                <td className="py-2 pr-2">
                  {/* ▼ 編集中の行だけテキスト入力に切り替える（インライン編集） */}
                  {editingId === tag.id ? (
                    <div className="space-y-1">
                      <Input
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        maxLength={50}
                        autoFocus
                      />
                      {editError && (
                        <p className="text-xs text-destructive">{editError}</p>
                      )}
                    </div>
                  ) : (
                    tag.name
                  )}
                </td>
                <td className="py-2 pr-2">
                  <Badge variant={tag.type === "default" ? "secondary" : "outline"}>
                    {tag.type === "default" ? "既定" : "独自"}
                  </Badge>
                </td>
                <td className="py-2 pr-2 text-muted-foreground">
                  {new Date(tag.createdAt).toLocaleDateString("ja-JP")}
                </td>
                <td className="py-2 text-right">
                  {/* ▼ type === "user" の行にだけ操作ボタンを出す。
                      default タグは編集・削除すると403になるため、そもそもボタンを出さない。 */}
                  {tag.type === "user" &&
                    (editingId === tag.id ? (
                      <div className="flex justify-end gap-1">
                        <Button
                          size="sm"
                          onClick={() => handleUpdate(tag.id)}
                          disabled={editingName.trim().length === 0 || updateTag.isPending}
                        >
                          保存
                        </Button>
                        <Button size="sm" variant="outline" onClick={cancelEditing}>
                          取消
                        </Button>
                      </div>
                    ) : (
                      <div className="flex justify-end gap-1">
                        <Button size="sm" variant="outline" onClick={() => startEditing(tag)}>
                          編集
                        </Button>
                        <Button
                          size="sm"
                          variant="destructive"
                          onClick={() => setDeleteTarget(tag)}
                        >
                          削除
                        </Button>
                      </div>
                    ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* ▼ 独自タグが1つも無いときだけ空状態メッセージを出す（default タグは必ずある想定） */}
      {!isLoading && userTags.length === 0 && (
        <p className="text-sm text-muted-foreground">まだ独自タグはありません</p>
      )}

      {/* ▼ 削除確認ダイアログ（UC-09：削除しても学習記録自体は消えないことを伝える） */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>タグを削除しますか？</DialogTitle>
            <DialogDescription>
              「{deleteTarget?.name}」を削除します。このタグが付いている学習記録は削除されず、タグとの紐付けだけが解除されます。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              キャンセル
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleteTag.isPending}>
              {deleteTag.isPending ? "削除中..." : "削除する"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
