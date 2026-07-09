"use client"

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】このファイルは shadcn/ui が自動生成するボイラープレート。覚えなくていい。
//   → Tailwind クラス名・radix-ui の LabelPrimitive の使い方は暗記不要
// ============================================================
import * as React from "react"
import { Label as LabelPrimitive } from "radix-ui"

import { cn } from "@/lib/utils"

function Label({
  className,
  ...props
}: React.ComponentProps<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      data-slot="label"
      className={cn(
        "flex items-center gap-2 text-sm leading-none font-medium select-none group-data-[disabled=true]:pointer-events-none group-data-[disabled=true]:opacity-50 peer-disabled:cursor-not-allowed peer-disabled:opacity-50",
        className
      )}
      {...props}
    />
  )
}

export { Label }
