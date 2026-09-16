"use client"

// 設定画面（/settings）。プロフィール（表示名）とパスワード変更の2セクションを縦に並べる。
// 各セクションは自分専用のフォーム・保存ボタンを持ち、保存単位が独立している
// （docs/settings/screen/overview.md の設計方針。GitHub/Vercel/Notion等の一般的なSaaS設定画面と同じ形）
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"

import { useProfile } from "@/hooks/useProfile"
import { useUpdateProfile, useUpdatePassword } from "@/hooks/useProfileMutations"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"

const profileSchema = z.object({
  displayName: z
    .string()
    .min(1, { message: "表示名を入力してください" })
    .max(100, { message: "表示名は100文字以内で入力してください" }),
})
type ProfileFormValues = z.infer<typeof profileSchema>

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, { message: "現在のパスワードを入力してください" }),
    newPassword: z
      .string()
      .min(8, { message: "8〜72文字で入力してください" })
      .max(72, { message: "8〜72文字で入力してください" }),
  })
  // クライアント側でも「新しいパスワードが現在のパスワードと同一」を弾く。
  // サーバー側の400を待たずにその場でエラー表示できる（docs/settings/screen/overview.md）。
  .refine((data) => data.newPassword !== data.currentPassword, {
    message: "新しいパスワードは現在のパスワードと異なるものにしてください",
    path: ["newPassword"],
  })
type PasswordFormValues = z.infer<typeof passwordSchema>

export default function SettingsPage() {
  const { data: profile, isLoading, isError } = useProfile()

  if (isError) {
    return (
      <div className="container mx-auto max-w-2xl p-6">
        <p className="text-muted-foreground">
          設定情報の取得に失敗しました。ページを再読み込みしてください。
        </p>
      </div>
    )
  }

  return (
    <div className="container mx-auto max-w-2xl space-y-6 p-6">
      <h1 className="text-2xl font-bold">設定</h1>

      {isLoading || !profile ? (
        <Skeleton className="h-48 w-full rounded-xl" />
      ) : (
        <ProfileSection profile={profile} />
      )}

      <PasswordSection />
    </div>
  )
}

// ▼ プロフィールセクション：表示名の変更
function ProfileSection({
  profile,
}: {
  profile: { email: string; displayName: string; createdAt: string }
}) {
  const updateProfile = useUpdateProfile()
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const form = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { displayName: profile.displayName },
  })

  const onSubmit = (data: ProfileFormValues) => {
    setSuccessMessage(null)
    updateProfile.mutate(data, {
      onSuccess: () => {
        setSuccessMessage("更新しました")
        // 数秒で自動的に消す
        setTimeout(() => setSuccessMessage(null), 3000)
      },
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>プロフィール</CardTitle>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            {/* ▼ メールアドレス：変更不可なので入力欄にせず、そのまま表示する */}
            <div className="space-y-1">
              <Label>メールアドレス</Label>
              <p className="text-sm text-muted-foreground">{profile.email}</p>
            </div>

            {/* ▼ 表示名：編集可能 */}
            <FormField
              control={form.control}
              name="displayName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>表示名</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* ▼ 登録日：表示のみ。時刻は出さず日付だけにする */}
            <div className="space-y-1">
              <Label>登録日</Label>
              <p className="text-sm text-muted-foreground">
                {profile.createdAt.slice(0, 10)}
              </p>
            </div>

            {updateProfile.isError && (
              <p className="text-sm text-destructive">
                保存に失敗しました。しばらく経ってからお試しください
              </p>
            )}
            {successMessage && (
              <p className="text-sm text-muted-foreground">{successMessage}</p>
            )}

            <div className="flex justify-end">
              <Button type="submit" disabled={updateProfile.isPending}>
                {updateProfile.isPending ? "保存中..." : "保存する"}
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}

// ▼ パスワード変更セクション
function PasswordSection() {
  const updatePassword = useUpdatePassword()
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const form = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: "", newPassword: "" },
  })

  // サーバーから403（現在のパスワード不一致）が返ってきたときのメッセージ。
  // フィールド固有のエラーではなくAPIから来た結果なので、react-hook-formのバリデーション
  // エラーとは別にuseStateで持つ（login/records系の画面と同じ「apiErrorは別管理」の方針）。
  const [apiError, setApiError] = useState<string | null>(null)

  const onSubmit = (data: PasswordFormValues) => {
    setApiError(null)
    setSuccessMessage(null)
    updatePassword.mutate(data, {
      onSuccess: () => {
        // 平文パスワードを画面に残さないよう入力欄をクリアする
        form.reset({ currentPassword: "", newPassword: "" })
        setSuccessMessage("パスワードを変更しました")
        setTimeout(() => setSuccessMessage(null), 3000)
      },
      onError: (error: unknown) => {
        const status = (error as { response?: { status?: number } })?.response?.status
        if (status === 403) {
          setApiError("現在のパスワードが正しくありません")
        } else {
          setApiError("変更に失敗しました。しばらく経ってからお試しください")
        }
      },
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>パスワード変更</CardTitle>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="currentPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>現在のパスワード</FormLabel>
                  <FormControl>
                    <Input type="password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="newPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>新しいパスワード</FormLabel>
                  <FormControl>
                    <Input type="password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {apiError && <p className="text-sm text-destructive">{apiError}</p>}
            {successMessage && (
              <p className="text-sm text-muted-foreground">{successMessage}</p>
            )}

            <div className="flex justify-end">
              <Button type="submit" disabled={updatePassword.isPending}>
                {updatePassword.isPending ? "変更中..." : "変更する"}
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}
