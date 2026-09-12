// ある学習記録に紐づく添付ファイル一覧を取得するフック
import { useQuery } from "@tanstack/react-query"
import type { Attachment } from "@/lib/api-types"
import api from "@/lib/api"

export function useAttachments(learningRecordId: string) {
  return useQuery<Attachment[]>({
    // queryKey に learningRecordId を含めるので、記録ごとに別キャッシュになる。
    // アップロード・削除の成功時に invalidateQueries({ queryKey: ["attachments", learningRecordId] })
    // を呼ぶと、この一覧だけが無効化されて再取得される。
    queryKey: ["attachments", learningRecordId],
    queryFn: () =>
      api
        .get<Attachment[]>(`/learning-records/${learningRecordId}/attachments`)
        .then((res) => res.data),
  })
}
