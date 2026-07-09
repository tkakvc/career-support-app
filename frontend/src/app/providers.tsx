"use client";

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ TanStack Query（QueryClientProvider）を使うか
//   → サーバーから取得するデータ（学習記録一覧など）のキャッシュ・ローディング状態・
//     エラー状態を自動管理してくれるから。
//     useState + useEffect で書くと「取得中かどうか」「エラーか」を自分で管理しないといけないが、
//     TanStack Query を使えば useQuery が isLoading / isError / data を自動で返してくれる。
//     これは「サーバー状態」（APIから来るデータ）と「ローカル状態」（UIの表示状態）を分離する設計。
// 【AI任せでOK】QueryClient の new の書き方・ReactQueryDevtools の設置方法
// ============================================================
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";

const queryClient = new QueryClient();

// 【Providers が大文字から始まる理由】
// JSX の中で <providers /> と書くと React は「HTML タグ」だと判断する（<div> や <span> と同じ扱い）。
// <Providers /> と大文字で書いて初めて「この名前の関数を呼び出して、返ってきた JSX を描画する」と判断する。
// つまり大文字・小文字は React が「HTML タグか？関数（コンポーネント）か？」を見分ける唯一の目印。
// Providers 自体は普通の関数で、JSX を return しているだけ。
export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
