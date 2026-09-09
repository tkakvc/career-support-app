// ============================================================
// shadcn/ui が自動生成するボイラープレート。
// cn() は「Tailwind のクラス名を条件付きで合成するユーティリティ関数」。
// clsx で条件分岐しながらクラス名を組み立て、twMerge で Tailwind の競合を解決する。
// ============================================================
import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
