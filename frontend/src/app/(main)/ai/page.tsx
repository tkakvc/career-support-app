"use client"

// ============================================================
// 設計は docs/ai/screen/overview.md・docs/ai/screen/flow.md、API仕様は docs/ai/api/request-response.md。
// ガードレールの詳細は docs/ai/guardrail-design.md、非同期化の詳細は
// memo/ai/AI提案の非同期化(SQS).md 参照。
//
// 【学習ポイント：押さえておく】なぜ「送信→ポーリング」の2段階になっているか
//   → Web検索＋OpenAI呼び出しは数秒〜1分ほどかかる可能性があり、それをHTTPリクエストの中で
//     待たせるとALBのタイムアウトやWebサーバーのスレッド/コネクションプール専有の
//     問題が起きる（memo/java/tomcat-thread-pool.md）。そこでSQSでバックグラウンド処理に
//     切り離し、フロントは「受け付けたjobId」を受け取ってから、結果ができるまで
//     数秒おきに問い合わせる（ポーリングする）形にしている。
//
// 【学習ポイント：押さえておく】参考資料（AiReference）は生成が完了した時点でサーバー側に自動保存済み。
// 画面下の「学習記録をつける」は、それとは別物の学習記録（learning_records）を新規作成する
// 完全に任意の追加アクションで、参考資料自体の保存とは無関係（押さなくても一覧に残り続ける）。
// ============================================================

import { useState } from "react"
import { useSearchParams } from "next/navigation"

import { useGenerateReference, useAiJob, useAiReferences } from "@/hooks/useAiMutations"
import { useCreateLearningRecord } from "@/hooks/useCreateLearningRecord"
import { useCreateTag } from "@/hooks/useTagMutations"
import { useTags } from "@/hooks/useTags"
import type { AiReference } from "@/lib/api-types"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"

// ▼ APIエラー（axios）から HTTP ステータスを取り出すヘルパー（tags/page.tsx と同じ判定の仕方）
function getErrorStatus(error: unknown): number | undefined {
  if (error && typeof error === "object" && "response" in error) {
    return (error as { response?: { status?: number } }).response?.status
  }
  return undefined
}

// 【学習ポイント：押さえておく】ステータスコードごとに表示文言を分けているのは、429（レート制限・
// 連打）と400（入力フィルタ／Moderation NG）はユーザーが自分の操作を見直せば直る類のエラーで、
// それ以外（500＝OpenAI・Web検索側の障害等）は「待つ」以外にユーザー側でできることが無いから。
function errorMessage(error: unknown): string {
  const status = getErrorStatus(error)
  if (status === 429) {
    return "本日のAI利用上限、または処理中のリクエストの上限に達しました。しばらくしてから再度お試しください"
  }
  if (status === 400) {
    return "入力内容に不適切な部分が含まれているか、内容が長すぎます"
  }
  return "現在AIサービスが利用できません。しばらく経ってから再度お試しください"
}

// summaryHtmlはOpenAIが生成したMarkdownをHTML化したもの。学習記録のcontent欄（プレーンテキスト）の
// 初期値としてそのまま貼ると崩れるため、タグを取り除いた簡易プレビュー用途にだけ使う
function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim()
}

export default function AiPage() {
  // ▼ 学習記録の詳細画面から生成した場合、/ai?jobId=... で遷移してくる。
  // その場合は最初からそのjobIdをポーリング対象にする（もう一度ボタンを押させない）
  const searchParams = useSearchParams()
  const [interest, setInterest] = useState("")
  const [jobId, setJobId] = useState<string | null>(() => searchParams.get("jobId"))
  const [selectedReference, setSelectedReference] = useState<AiReference | null>(null)
  const [tagFilter, setTagFilter] = useState<string>("")

  const generate = useGenerateReference()
  const job = useAiJob(jobId)
  const { data: tags } = useTags()
  const references = useAiReferences(tagFilter === "" ? null : tagFilter)

  const isBusy = generate.isPending || job.data?.status === "PENDING" || job.data?.status === "PROCESSING"
  // ▼ jobIdが無いのにmessageがある＝学習記録0件などの即時応答（202ではなく200で返ってきたケース）
  const immediateMessage = jobId === null ? generate.data?.message : undefined
  const jobResult = job.data?.status === "DONE" ? job.data.result : undefined

  // ▼ ジョブの結果が出た瞬間、または一覧のカードをクリックした瞬間に、その参考資料を「結果表示」にする
  const displayedReference = selectedReference ?? jobResult

  const handleGenerate = () => {
    setJobId(null)
    setSelectedReference(null)
    generate.mutate(
      { interest: interest.trim() === "" ? undefined : interest },
      { onSuccess: (response) => setJobId(response.jobId ?? null) }
    )
  }

  return (
    <div className="container mx-auto max-w-2xl p-6 space-y-6">
      <h1 className="text-2xl font-bold">AI 情報収集・提案</h1>

      {/* ▼ 外部送信の開示（docs/ai/guardrail-design.md の8）。常時表示、同意チェックボックスは設けない */}
      <p className="text-xs text-muted-foreground">
        ※ 入力内容はAIによる分析のため OpenAI 社・Web検索サービスに送信されます
      </p>

      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">
          最近の学習記録と、興味のある技術・分野をもとにWeb検索を行い、参考資料（要約＋参考リンク）を生成します
        </p>
        <div className="space-y-1">
          <Input
            placeholder="例：バックエンドを伸ばしたい（任意）"
            value={interest}
            onChange={(e) => setInterest(e.target.value)}
            maxLength={200}
          />
          <p className="text-xs text-muted-foreground">{interest.length}/200文字（任意）</p>
        </div>
        <Button onClick={handleGenerate} disabled={isBusy}>
          {isBusy ? "生成中..." : "参考資料を生成する"}
        </Button>

        {isBusy && (
          <p className="text-sm text-muted-foreground">AIが検索・要約しています（数秒〜1分ほどかかることがあります）</p>
        )}

        {generate.isError && <p className="text-sm text-destructive">{errorMessage(generate.error)}</p>}
        {job.data?.status === "FAILED" && <p className="text-sm text-destructive">{job.data.message}</p>}
        {immediateMessage && <p className="text-sm text-muted-foreground">{immediateMessage}</p>}
      </div>

      {displayedReference && (
        <ReferenceResult
          reference={displayedReference}
          onClose={() => {
            setSelectedReference(null)
            setJobId(null)
          }}
        />
      )}

      <div className="space-y-3 border-t pt-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">保存済みの参考資料</h2>
          <select
            className="text-sm border rounded px-2 py-1"
            value={tagFilter}
            onChange={(e) => setTagFilter(e.target.value)}
          >
            <option value="">すべてのタグ</option>
            {tags?.map((tag) => (
              <option key={tag.id} value={tag.id}>
                {tag.name}
              </option>
            ))}
          </select>
        </div>

        {references.data?.references.length === 0 && (
          <p className="text-sm text-muted-foreground">まだ参考資料がありません</p>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {references.data?.references.map((reference) => (
            <button
              key={reference.id}
              onClick={() => {
                setSelectedReference(reference)
                setJobId(null)
              }}
              className="text-left border rounded p-3 hover:bg-muted transition-colors"
            >
              <p className="text-sm font-medium">{reference.interest ?? reference.tagName ?? reference.suggestedTagName}</p>
              <p className="text-xs text-muted-foreground mt-1">
                {reference.tagName ?? `${reference.suggestedTagName}（未作成）`} ・{" "}
                {new Date(reference.createdAt).toLocaleDateString()}
              </p>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

// ▼ iframeでの要約表示 ＋ 学習記録をつけるフォーム（同時に表示。生成直後・一覧クリック時とも共通）
function ReferenceResult({ reference, onClose }: { reference: AiReference; onClose: () => void }) {
  const createLearningRecord = useCreateLearningRecord()
  const createTag = useCreateTag()

  const today = new Date().toISOString().slice(0, 10)
  const defaultContent = `${reference.interest ?? reference.tagName ?? reference.suggestedTagName}\n\n${stripHtml(reference.summaryHtml).slice(0, 400)}`

  const [content, setContent] = useState(defaultContent)
  const [duration, setDuration] = useState("5")
  const [saved, setSaved] = useState(false)
  const [tagError, setTagError] = useState<string | null>(null)

  // 【学習ポイント：押さえておく】参考資料のtagIdが無い（＝AIが提案しただけでまだ存在しないタグ名）
  // 場合、ここで初めてタグを作る。この一連の処理はユーザーが「学習記録をつける」ボタンを押した
  // 瞬間に実行される、明示的な操作起点のリクエストなので、AIの非同期ジョブが勝手にタグを
  // 作ってしまう問題（バックエンド側で解消済み）とは違い問題ない
  const handleAddToLearningRecord = async () => {
    setTagError(null)
    let tagId = reference.tagId

    if (!tagId && reference.suggestedTagName) {
      try {
        const created = await createTag.mutateAsync({ name: reference.suggestedTagName })
        tagId = created.id
      } catch {
        setTagError("タグの作成に失敗しました。しばらく経ってからお試しください")
        return
      }
    }

    createLearningRecord.mutate(
      {
        date: today,
        content,
        duration: parseInt(duration, 10),
        tagIds: tagId ? [tagId] : [],
      },
      { onSuccess: () => setSaved(true) }
    )
  }

  return (
    <div className="border rounded overflow-hidden">
      <div className="flex items-center justify-between bg-muted px-3 py-2">
        <p className="text-sm font-medium">参考資料</p>
        <button onClick={onClose} className="text-xs text-muted-foreground hover:underline">
          閉じる
        </button>
      </div>

      {/* ▼ 要約HTMLをiframeで表示する。OpenAIが生成した内容なのでdangerouslySetInnerHTMLではなく
          iframeに隔離して表示する（親ページのCSS・スクリプトに影響させないため） */}
      <iframe title="参考資料の要約" srcDoc={reference.summaryHtml} className="w-full h-80 border-b" />

      {reference.links.length > 0 && (
        <div className="px-3 py-2 border-b">
          <p className="text-xs font-medium text-muted-foreground mb-1">参考リンク</p>
          <ul className="space-y-1">
            {reference.links.map((link, i) => (
              <li key={i} className="text-xs">
                <a href={link.url} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">
                  {link.title ?? link.url}
                </a>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="p-3 space-y-3">
        <p className="text-sm font-medium">別途、学習記録としても残したい場合</p>

        {saved ? (
          <p className="text-sm text-muted-foreground">学習記録として保存しました</p>
        ) : (
          <>
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">内容</p>
              <Textarea value={content} onChange={(e) => setContent(e.target.value)} rows={4} maxLength={2000} />
            </div>
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">学習時間（分）</p>
              <Input
                type="number"
                min={1}
                max={1440}
                value={duration}
                onChange={(e) => setDuration(e.target.value)}
                className="w-24"
              />
            </div>
            <p className="text-xs text-muted-foreground">
              タグ：{reference.tagName ?? `${reference.suggestedTagName}（新規作成されます）`}
            </p>
            <Button
              onClick={handleAddToLearningRecord}
              disabled={createLearningRecord.isPending || createTag.isPending}
            >
              {createLearningRecord.isPending || createTag.isPending ? "保存中..." : "学習記録をつける"}
            </Button>
            {tagError && <p className="text-sm text-destructive">{tagError}</p>}
            {createLearningRecord.isError && (
              <p className="text-sm text-destructive">保存に失敗しました。しばらく経ってからお試しください</p>
            )}
          </>
        )}
      </div>
    </div>
  )
}
