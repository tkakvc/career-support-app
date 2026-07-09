// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】このファイルは shadcn/ui が自動生成するボイラープレート。覚えなくていい。
//   → Tailwind クラス名の羅列（h-8, rounded-lg, border-input, aria-invalid:... など）は暗記不要
//   → cn() でクラス名をマージする書き方はパターンとして覚えておくと役立つ
// 【面接で説明できるようにする】なぜ className を {...props} の前に cn() で合成するか
//   → 呼び出し側が className を渡したとき、デフォルトのクラス名と競合せずに上書きできるようにするため。
//     Tailwind Merge（twMerge）が同じプロパティのクラスを後から渡した方で上書きしてくれる。
// ============================================================
import * as React from "react"

import { cn } from "@/lib/utils"

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:disabled:bg-input/80 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        className
      )}
      {...props}
    />
  )
}

export { Input }
