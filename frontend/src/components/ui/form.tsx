"use client"

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】このファイルは shadcn/ui が自動生成するボイラープレート。中身を手書きしなくていい。
//   → FormItem / FormLabel / FormControl / FormMessage の JSX 構造は暗記不要
//   → React Context（createContext / useContext）を使った実装の詳細は読み流してOK
// 【面接で説明できるようにする】「なぜ FormProvider（= Form）が必要か」は説明できるようにする
//   → useForm() が返す methods（control, formState など）を、
//     FormField / FormLabel / FormMessage が親から受け取らずに useFormContext() で取り出せるから。
//     props バケツリレーを避けるために React Context を使っているのが shadcn/ui の設計。
// ============================================================
// shadcn/ui の Form コンポーネント群。
// react-hook-form の FormProvider・useFormContext・Controller をラップして、
// エラー表示・ラベル・入力欄を一まとめにした「フォームの部品セット」を提供する。

import * as React from "react"
import {
  Controller,
  FormProvider,
  useFormContext,
  type ControllerProps,
  type FieldPath,
  type FieldValues,
} from "react-hook-form"
import { Slot } from "radix-ui"

import { cn } from "@/lib/utils"
import { Label } from "@/components/ui/label"

// ▼ Form
// react-hook-form の FormProvider をそのままエクスポートする。
// useForm() が返す methods を <Form {...methods}> に渡すだけで、
// フォーム内のどのコンポーネントからでも useFormContext() で methods を取り出せるようになる。
const Form = FormProvider

// ▼ FormFieldContext
// どのフィールドの中にいるかを子コンポーネントに伝えるための React Context。
// Context は「props を使わずに親から子孫に値を渡す仕組み」。
type FormFieldContextValue<
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
> = {
  name: TName
}

const FormFieldContext = React.createContext<FormFieldContextValue>(
  {} as FormFieldContextValue
)

// ▼ FormField
// react-hook-form の Controller をラップする。
// Controller は「フォームの1つの入力欄を react-hook-form の管理下に置く」コンポーネント。
// <FormField name="email" render={({ field }) => <Input {...field} />} /> のように使う。
// field には value・onChange・onBlur などが入っており、Input に spread すると RHF と繋がる。
const FormField = <
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
>({
  ...props
}: ControllerProps<TFieldValues, TName>) => {
  return (
    <FormFieldContext.Provider value={{ name: props.name }}>
      <Controller {...props} />
    </FormFieldContext.Provider>
  )
}

// ▼ useFormField
// FormItem / FormLabel / FormMessage の中で使う共通フック。
// FormFieldContext から name を取り出し、useFormContext() でそのフィールドの状態を取得する。
// fieldState.error に値があれば「バリデーションエラーあり」。
const useFormField = () => {
  const fieldContext = React.useContext(FormFieldContext)
  const { getFieldState, formState } = useFormContext()
  const fieldState = getFieldState(fieldContext.name, formState)

  return {
    name: fieldContext.name,
    ...fieldState,
  }
}

// ▼ FormItemContext
// FormItem が生成した id を子コンポーネント（FormLabel・FormControl・FormMessage）に伝える。
// id は HTML の <label for="xxx"> と <input id="xxx"> を紐付けるために使う。
type FormItemContextValue = {
  id: string
}

const FormItemContext = React.createContext<FormItemContextValue>(
  {} as FormItemContextValue
)

// ▼ FormItem
// 1つの入力欄のまとまり（ラベル + 入力 + エラーメッセージ）を囲む div。
// 一意な id を生成して FormItemContext に渡す。
const FormItem = ({ className, ...props }: React.ComponentProps<"div">) => {
  const id = React.useId()

  return (
    <FormItemContext.Provider value={{ id }}>
      <div
        data-slot="form-item"
        className={cn("grid gap-2", className)}
        {...props}
      />
    </FormItemContext.Provider>
  )
}

// ▼ FormLabel
// <label> タグのラッパー。
// バリデーションエラーがあるとき、文字色を赤（text-destructive）に変える。
// htmlFor に FormItemContext の id を自動セットするので、<label for="xxx"> を手書きしなくていい。
const FormLabel = ({ className, ...props }: React.ComponentProps<typeof Label>) => {
  const { error } = useFormField()
  const { id } = React.useContext(FormItemContext)

  return (
    <Label
      data-slot="form-label"
      data-error={!!error}
      className={cn("data-[error=true]:text-destructive", className)}
      htmlFor={id}
      {...props}
    />
  )
}

// ▼ FormControl
// 入力欄（Input など）を包むラッパー。
// Slot を使って「第1子コンポーネントにそのまま props を渡す」ことで、
// id・aria-describedby・aria-invalid を Input に付与する。
// aria-invalid="true" があると Input の CSS が自動でエラー表示用スタイルに変わる。
const FormControl = ({ ...props }: React.ComponentProps<typeof Slot.Root>) => {
  const { error } = useFormField()
  const { id } = React.useContext(FormItemContext)

  return (
    <Slot.Root
      data-slot="form-control"
      id={id}
      aria-invalid={!!error}
      {...props}
    />
  )
}

// ▼ FormMessage
// バリデーションエラーのメッセージを表示する <p> タグ。
// error がなければ何も表示しない（null を返す）。
// error がある場合は error.message（Zod のエラーメッセージ）を表示する。
const FormMessage = ({ className, children, ...props }: React.ComponentProps<"p">) => {
  const { error } = useFormField()
  const body = error ? String(error?.message ?? "") : children

  if (!body) {
    return null
  }

  return (
    <p
      data-slot="form-message"
      className={cn("text-destructive text-sm", className)}
      {...props}
    >
      {body}
    </p>
  )
}

export {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  useFormField,
}
