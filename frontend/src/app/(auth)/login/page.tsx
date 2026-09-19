"use client"

// ============================================================
// なぜ react-hook-form + Zod でバリデーションするか
//   → 入力値の検証をフォームの状態管理と一体化することで、
//     送信ボタン押下時だけでなくフィールドごとにリアルタイムでエラーを出せる。
//     素の HTML <form> + fetch では実現が難しいUX改善が目的。
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
import { AuthResponse, UserProfile } from "@/lib/api-types"
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

// なぜ Zod でバリデーションスキーマを定義するか
//   → TypeScript の型と「実行時の値チェック」を一か所にまとめられるから。
//     z.infer<typeof loginSchema> で型を自動生成できるので、型定義とバリデーションが常に一致する。
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

  // なぜ apiError を useState で、バリデーションエラーは react-hook-form で管理するか
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
      // レスポンスの型は AuthResponse（{ accessToken, expiresIn }）。
      // リフレッシュトークンはレスポンス本文には入っていない。サーバーが
      // Set-Cookie（HttpOnly）で直接ブラウザに保存するため、フロントのJavaScriptは
      // その値を受け取らない・扱わない。
      const response = await api.post<AuthResponse>("/auth/login", {
        email: data.email,
        password: data.password,
      })

      // ▼ ログイン成功：アクセストークンをZustandのメモリ上に保存する
      // （このストアはpersistしていないので、リロードすると消える。
      //   その場合は (main)/layout.tsx のサイレントリフレッシュでCookieから復元する）
      const { accessToken } = response.data

      // ▼ userId・displayName・email は GET /api/users/me から取得する
      // まだ setAuth を呼んでいない（authStoreにトークンが無い）ため、
      // axios の interceptor には頼らず、今取れたばかりの accessToken を直接ヘッダーに指定する。
      const profile = await api
        .get<UserProfile>("/users/me", {
          headers: { Authorization: `Bearer ${accessToken}` },
        })
        .then((res) => res.data)

      setAuth(accessToken, profile.id, profile.displayName, profile.email)

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
