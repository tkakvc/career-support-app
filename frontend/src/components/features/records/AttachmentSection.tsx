"use client"

// 学習記録の詳細画面（/records/{id} の表示モード）に組み込む添付ファイルセクション。
// 独立した画面は持たず、詳細画面の一部として一覧・アップロード・ダウンロード・削除を行う。
import { useRef, useState } from "react"
import { useAttachments } from "@/hooks/useAttachments"
import {
  useUploadAttachment,
  useDeleteAttachment,
  downloadAttachment,
} from "@/hooks/useAttachmentMutations"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog"
import type { Attachment } from "@/lib/api-types"

// バックエンド（AttachmentService）の制約に合わせる
const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
const MAX_ATTACHMENT_COUNT = 10

// 102400（バイト）→ "100 KB" のように整形する
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

type Props = {
  learningRecordId: string
}

export function AttachmentSection({ learningRecordId }: Props) {
  const { data: attachments, isLoading, isError } = useAttachments(learningRecordId)
  const uploadMutation = useUploadAttachment(learningRecordId)
  const deleteMutation = useDeleteAttachment(learningRecordId)

  // ▼ useRef の前提知識（React 固有の丸暗記事項。文脈から推測できるものではない）
  //   useRef(初期値) は必ず { current: 初期値 } という形のオブジェクトを返す、という決まりごと。
  //   <input ref={fileInputRef}> と書くと、React がその <input> を実際に作った後で
  //   fileInputRef.current にその本物の DOM 要素を勝手に入れてくれる（最初は null）。
  //   なので以降 fileInputRef.current は「本物の <input> 要素」を指し、
  //   fileInputRef.current.value のような普通の DOM 操作がそのままできる。
  // <input type="file"> を JS から開く/クリアするための参照
  const fileInputRef = useRef<HTMLInputElement>(null)
  // アップロードのバリデーション/失敗メッセージ
  const [uploadError, setUploadError] = useState<string | null>(null)
  // 削除確認ダイアログの対象（null なら閉じている）
  const [deleteTarget, setDeleteTarget] = useState<Attachment | null>(null)
  // ダウンロード中の行を示す（連打防止と表示用）
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setUploadError(null)
    const file = e.target.files?.[0]
    if (!file) return

    // クライアント側で先にサイズを弾く（サーバーの400を待たずにその場でエラー表示できる）
    if (file.size > MAX_FILE_SIZE) {
      setUploadError("ファイルサイズは10MBまでです")
      // 同じファイルを選び直したときにも onChange が発火するよう value をクリアする
      if (fileInputRef.current) fileInputRef.current.value = ""
      return
    }

    uploadMutation.mutate(file, {
      onSuccess: () => {
        if (fileInputRef.current) fileInputRef.current.value = ""
      },
      onError: () => {
        setUploadError(
          "アップロードに失敗しました。時間をおいて再試行してください"
        )
        if (fileInputRef.current) fileInputRef.current.value = ""
      },
    })
  }

  const handleDownload = async (attachment: Attachment) => {
    setDownloadingId(attachment.id)
    try {
      await downloadAttachment(
        learningRecordId,
        attachment.id,
        attachment.fileName
      )
    } catch {
      setUploadError("ダウンロードに失敗しました。時間をおいて再試行してください")
    } finally {
      setDownloadingId(null)
    }
  }

  const handleDelete = () => {
    if (!deleteTarget) return
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => setDeleteTarget(null),
    })
  }

  const count = attachments?.length ?? 0
  const remaining = MAX_ATTACHMENT_COUNT - count
  const isFull = count >= MAX_ATTACHMENT_COUNT

  return (
    <section className="space-y-3 border-t pt-4">
      <h2 className="text-sm font-semibold text-muted-foreground">添付ファイル</h2>

      {/* ▼ 一覧 */}
      {isLoading && (
        <div className="space-y-2">
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-8 w-full" />
        </div>
      )}

      {isError && (
        <p className="text-sm text-destructive">
          添付ファイルの取得に失敗しました。
        </p>
      )}

      {!isLoading && !isError && count === 0 && (
        <p className="text-sm text-muted-foreground">添付ファイルはありません</p>
      )}

      {!isLoading && !isError && count > 0 && (
        <ul className="divide-y rounded-md border">
          {attachments!.map((a) => (
            <li
              key={a.id}
              className="flex items-center justify-between gap-2 px-3 py-2 text-sm"
            >
              <span className="min-w-0 flex-1 truncate">{a.fileName}</span>
              <span className="shrink-0 text-muted-foreground">
                {formatFileSize(a.fileSize)}
              </span>
              <div className="flex shrink-0 gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => handleDownload(a)}
                  disabled={downloadingId === a.id}
                >
                  {downloadingId === a.id ? "取得中..." : "DL"}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setDeleteTarget(a)}
                >
                  削除
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* ▼ アップロード */}
      {isFull ? (
        <p className="text-sm text-muted-foreground">
          添付ファイルは上限（{MAX_ATTACHMENT_COUNT}件）に達しています
        </p>
      ) : (
        <div className="space-y-1">
          <input
            ref={fileInputRef}
            type="file"
            onChange={handleFileChange}
            disabled={uploadMutation.isPending}
            className="block text-sm file:mr-3 file:rounded-md file:border file:border-input file:bg-transparent file:px-3 file:py-1 file:text-sm"
          />
          <p className="text-xs text-muted-foreground">
            {uploadMutation.isPending
              ? "アップロード中..."
              : `10MB以下・残り${remaining}ファイルまで`}
          </p>
        </div>
      )}

      {uploadError && <p className="text-sm text-destructive">{uploadError}</p>}

      {/* ▼ 削除確認ダイアログ */}
      <Dialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>添付ファイルを削除しますか？</DialogTitle>
            <DialogDescription>
              「{deleteTarget?.fileName}」を削除します。この操作は取り消せません。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setDeleteTarget(null)}
            >
              キャンセル
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? "削除中..." : "削除する"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
