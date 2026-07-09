// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】このファイルは shadcn/ui が自動生成するボイラープレート。覚えなくていい。
//   → cn() は「Tailwind のクラス名を条件付きで合成するユーティリティ関数」。
//     clsx で条件分岐しながらクラス名を組み立て、twMerge で Tailwind の競合を解決する。
//   → shadcn/ui のコンポーネントが内部で使っている関数なので、基本的には触らない。
// ============================================================
import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
