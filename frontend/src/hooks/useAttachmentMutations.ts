// 添付ファイルのアップロード・削除フック、およびダウンロード関数
import { useMutation, useQueryClient } from "@tanstack/react-query"
import type { Attachment } from "@/lib/api-types"
import api from "@/lib/api"

// ▼ useUploadAttachment: ファイルを1件アップロードする
// mutate(file) で呼ぶ（<input type="file"> で選択した File オブジェクトをそのまま渡す）
export function useUploadAttachment(learningRecordId: string) {
  const queryClient = useQueryClient()

  return useMutation<Attachment, Error, File>({
    mutationFn: (file) => {
      // multipart/form-data で送るため FormData に詰める。
      // フィールド名 "file" はバックエンド（AttachmentController の @RequestParam MultipartFile file）と一致させる。
      const formData = new FormData()
      formData.append("file", file)
      // Content-Type は指定しない。FormData を渡すと axios が
      // multipart/form-data と boundary を自動で付けてくれる（手で書くと boundary が抜けて壊れる）。
      return api
        .post<Attachment>(
          `/learning-records/${learningRecordId}/attachments`,
          formData
        )
        .then((res) => res.data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["attachments", learningRecordId],
      })
    },
  })
}

// ▼ useDeleteAttachment: 添付ファイルを1件削除する
// mutate(attachmentId) で呼ぶ
export function useDeleteAttachment(learningRecordId: string) {
  const queryClient = useQueryClient()

  return useMutation<void, Error, string>({
    mutationFn: (attachmentId) =>
      api
        .delete(
          `/learning-records/${learningRecordId}/attachments/${attachmentId}`
        )
        .then(() => undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["attachments", learningRecordId],
      })
    },
  })
}

// ▼ ダウンロード: react-query の mutation ではなく普通の非同期関数。
// このエンドポイントは JWT 必須なので、単純な <a href="..."> では Authorization ヘッダーを
// 付けられずダウンロードできない。api インスタンス（interceptor で JWT を自動付与）で
// blob として取得し、その場で一時 URL を作ってブラウザに保存させる。
export async function downloadAttachment(
  learningRecordId: string,
  attachmentId: string,
  fileName: string
) {
  const res = await api.get(
    `/learning-records/${learningRecordId}/attachments/${attachmentId}/download`,
    { responseType: "blob" }
  )
  // res.data は「メモリ上のバイナリの塊（Blob）」。まだ URL は持っていない。
  const blob = res.data as Blob
  // createObjectURL: ブラウザ内部に「この URL 文字列 → この Blob」の対応を1件登録し、
  // blob:http://localhost:3000/xxxx という一時 URL を返す。
  const url = URL.createObjectURL(blob)
  // その URL を href に持つ <a download> を JS で作ってクリック → ブラウザの保存動作が走る。
  // download 属性の値が保存時のファイル名になる（一覧の fileName を使う）。
  const a = document.createElement("a")
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 使い終わった一時 URL を解放する。呼ばないとタブを閉じるまでメモリに残り続ける。
  URL.revokeObjectURL(url)
}
