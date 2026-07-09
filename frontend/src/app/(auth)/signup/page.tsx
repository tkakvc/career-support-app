"use client"
// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ react-hook-form + Zod でバリデーションするか
//   → フィールドごとにバリデーションエラーをリアルタイムで表示でき、UXが向上する。
//     Zod でスキーマを定義すると TypeScript 型も自動生成されるので、型定義の重複がない。
// 【AI任せでOK】Tailwind CSS のクラス名・shadcn/ui のフォームコンポーネント構造
// ============================================================
// ▼ このページが何をするか
// POST /api/auth/signup を呼んでアカウントを作成する。
// 成功したら /login にリダイレクト（登録完了後にログインさせる設計）。
// 失敗（409 Conflict）したら「このメールアドレスはすでに使用されています」を表示する。

import { useState } from "react"
// useState とは：
// コンポーネントの中で値を保持しておく仕組み。
// const [value, setValue] = useState(初期値) のように書く。
// setValue(新しい値) を呼ぶと value が更新され、コンポーネントが再レンダリングされる。

import { useRouter } from "next/navigation"
// useRouter とは：
// Next.js が提供するフック。router.push("/login") のようにプログラムからページ遷移できる。
// <a> タグをクリックさせるのではなく、コード側から遷移を制御したいときに使う。

import { useForm } from "react-hook-form"
// useForm とは：
// react-hook-form が提供するフック。フォームの状態（入力値・バリデーション結果・送信中かどうか）を管理する。
// useForm() を呼ぶと { register, handleSubmit, formState, control, ... } が返ってくる。

import { zodResolver } from "@hookform/resolvers/zod"
// zodResolver とは：
// react-hook-form と Zod を繋ぐアダプター。
// useForm({ resolver: zodResolver(schema) }) のように渡すと、
// フォーム送信時に Zod のスキーマでバリデーションを実行してくれる。

import { z } from "zod"
// z とは：
// Zod が提供するオブジェクト。z.string()・z.number()・z.object() のように使って
// バリデーションルールを定義する。

import api from "@/lib/api"
// api とは：
// lib/api.ts で作った axios インスタンス。
// Authorization ヘッダーの付与と 401 時のリダイレクトが自動で行われる設定済みの HTTP クライアント。

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
// Form 系のコンポーネントとは：
// shadcn/ui の Form は 6 つのパーツに分かれている。
//   Form        → react-hook-form の FormProvider のラッパー。useForm() の戻り値を渡す
//   FormField   → react-hook-form の Controller のラッパー。1つの入力欄を管理下に置く
//   FormItem    → ラベル + 入力欄 + エラーメッセージを囲む div
//   FormLabel   → <label> タグ。エラー時は文字色が赤くなる
//   FormControl → <Input> などに id と aria-invalid を付与する
//   FormMessage → バリデーションエラーを表示する <p>。エラーがなければ何も出さない

// 【AI任せでOK】z.string().min() / .refine() / .email() などの Zod 構文は覚えなくていい
// 【面接で説明できるようにする】なぜ Zod スキーマから TypeScript 型（z.infer）を生成するか
//   → スキーマと型を別々に書くと変更時に2箇所直す必要があり、ズレが起きやすい。
//     z.infer で型を自動生成することで、スキーマを変えるだけで型も追従する。
// ▼ Zod スキーマ：フォームのバリデーションルール
// z.object({}) の中に「フィールド名: ルール」を書く。
// このスキーマを zodResolver に渡すと、react-hook-form がフォーム送信時に自動でバリデーションを実行する。
const signupSchema = z.object({
  // Zod v4 では z.string().email() が非推奨になった。
  // 代わりに z.email() をスタンドアロンで使うか、.refine() の中で safeParse() を使う。
  // ここでは空欄（「入力してください」）と形式エラー（「有効な〜」）で別メッセージを出したいので
  // .min(1) で空欄チェック → .refine() で形式チェック の2段階にしている。
  // z.email().safeParse(val).success は「val が有効なメール形式なら true」を返す。
  email: z
    .string()
    .min(1, { message: "メールアドレスを入力してください" })
    .refine((val) => z.email().safeParse(val).success, {
      message: "有効なメールアドレスを入力してください",
    }),
  password: z
    .string()
    .min(8, { message: "パスワードは8文字以上で入力してください" }),
  displayName: z
    .string()
    .min(1, { message: "表示名を入力してください" }),
})

// ▼ SignupFormValues
// z.infer<typeof signupSchema> で「スキーマに合致する値の TypeScript 型」を自動生成する。
// これは { email: string; password: string; displayName: string } と同じ意味だが、
// スキーマと型定義を2箇所に書かなくて済む（スキーマを変えれば型も自動で変わる）。
type SignupFormValues = z.infer<typeof signupSchema>

export default function SignupPage() {
  const router = useRouter()

  // ▼ apiError：API が返したエラーメッセージを保持するローカル状態
  // バリデーションエラー（「8文字以上で入力してください」など）は react-hook-form が管理するので useState には入れない。
  // 401・409 など API から返ってくるエラーだけここに入れる。
  // null = エラーなし / 文字列 = エラーあり
  const [apiError, setApiError] = useState<string | null>(null)

  // ▼ useForm：フォームの状態管理を react-hook-form に委ねる
  // resolver: zodResolver(signupSchema) → 送信時に signupSchema でバリデーション実行
  // defaultValues → フォームの初期値。空文字で初期化しないと input が「非制御コンポーネント」になり警告が出る
  const form = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: {
      email: "",
      password: "",
      displayName: "",
    },
  })

  // ▼ onSubmit：フォームが送信されたときに呼ばれる関数
  // react-hook-form がバリデーション成功後にこの関数を呼ぶ。バリデーション失敗時は呼ばれない。
  // data には { email: "...", password: "...", displayName: "..." } が入っている。
  const onSubmit = async (data: SignupFormValues) => {
    // 前回の API エラーをリセット（再送信時に古いエラーが残らないようにする）
    setApiError(null)

    try {
      // ▼ POST /api/auth/signup を呼ぶ
      // api.post(url, body) で HTTP POST リクエストを送る。
      // レスポンスの中身は使わないので型引数は省略する。
      await api.post("/auth/signup", {
        email: data.email,
        password: data.password,
        displayName: data.displayName,
      })

      // 【面接で説明できるようにする】なぜ登録後に自動ログインさせず /login にリダイレクトするか
      //   → 「登録とログインは別操作」という設計。自動ログインにするなら登録後に
      //     /api/auth/login も呼んで JWT を取得し setAuth する必要がある。
      //     ここでは実装をシンプルにするため、登録後は手動ログインを求める設計にした。
      // ▼ サインアップ成功：/login にリダイレクト
      // 設計として「登録後は自分でログインしてもらう」にしているので setAuth は呼ばない。
      // 登録と同時に自動ログインさせるなら、ここで /api/auth/login も呼んで setAuth する。
      router.push("/login")
    } catch (error: unknown) {
      // ▼ エラーの型を絞り込む
      // axios のエラーは error.response.status に HTTP ステータスコードが入っている。
      // TypeScript は catch した値の型が unknown なので、"response" が存在するか確認してから参照する。
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        (error as { response?: { status?: number } }).response?.status === 409
        // 409 Conflict → バックエンドが「このメールアドレスはすでに登録済み」と返してきた
      ) {
        setApiError("このメールアドレスはすでに使用されています")
      } else {
        setApiError("登録に失敗しました。しばらく経ってからお試しください")
      }
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="w-full max-w-md space-y-6 p-8">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold">アカウント作成</h1>
          <p className="text-muted-foreground text-sm">
            必要事項を入力してアカウントを作成してください
          </p>
        </div>

        {/* ▼ <Form {...form}>
            form は useForm() が返すオブジェクト（register・control・handleSubmit などが入っている）。
            {...form} で Form（= FormProvider）に渡すと、フォーム内のどのコンポーネントからでも
            useFormContext() でこの methods を取り出せるようになる。 */}
        <Form {...form}>
          {/* ▼ form.handleSubmit(onSubmit)
              handleSubmit は「バリデーションを実行し、成功したら onSubmit を呼ぶ」ラッパー関数を返す。
              バリデーション失敗時は onSubmit が呼ばれず、エラーが FormMessage に自動で表示される。 */}
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">

            {/* ▼ メールアドレス入力欄
                FormField の render 関数が受け取る { field } とは：
                field = { value, onChange, onBlur, name, ref } の5つが入ったオブジェクト。
                <Input {...field} /> で Input に spread すると、
                ユーザーが入力するたびに react-hook-form の状態が更新される。 */}
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>メールアドレス</FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      placeholder="example@email.com"
                      {...field}
                    />
                  </FormControl>
                  {/* FormMessage は useFormContext() で email フィールドのエラーを取得して表示する。
                      エラーがなければ null を返して何も表示しない。 */}
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
                    {/* type="password" にすると入力内容が「●●●」で表示される */}
                    <Input type="password" placeholder="8文字以上" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* ▼ 表示名入力欄 */}
            <FormField
              control={form.control}
              name="displayName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>表示名</FormLabel>
                  <FormControl>
                    <Input type="text" placeholder="山田太郎" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* ▼ API エラーメッセージ（409 が返ってきたときに表示）
                apiError が null（= エラーなし）のときは && の右辺を評価せず何も表示しない。
                apiError に文字列が入っているときだけ <p> が表示される。 */}
            {apiError && (
              <p className="text-destructive text-sm">{apiError}</p>
            )}

            {/* ▼ 送信ボタン
                form.formState.isSubmitting は「onSubmit が実行中（= API 呼び出し待ち）のあいだ true」。
                disabled={true} の間はボタンがクリックできなくなり、二重送信を防ぐ。
                表示テキストも「登録中...」に切り替えて、ユーザーに処理中であることを伝える。 */}
            <Button
              type="submit"
              className="w-full"
              disabled={form.formState.isSubmitting}
            >
              {form.formState.isSubmitting ? "登録中..." : "アカウントを作成"}
            </Button>
          </form>
        </Form>

        <p className="text-center text-sm text-muted-foreground">
          すでにアカウントをお持ちの方は{" "}
          <a href="/login" className="text-primary underline">
            ログイン
          </a>
        </p>
      </div>
    </div>
  )
}
