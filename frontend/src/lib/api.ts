// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ axios に interceptor を使うか
//   → 全リクエストに共通処理（JWT の付与）と全レスポンスに共通処理（401 時のリダイレクト）を
//     1箇所にまとめるため。interceptor なしだと、API を呼ぶ全箇所で
//     Authorization ヘッダーを手書きしないといけなくなる。
// 【面接で説明できるようにする】なぜ 401 でリダイレクトするか
//   → 401 = サーバーが「このトークンは無効（期限切れ）」と判断した状態。
//     ユーザーは再認証が必要なのでログインページに強制誘導する。
//     各コンポーネントで 401 を個別に処理するより、1箇所で統一的に処理する方が保守性が高い。
// 【AI任せでOK】axios.create() の書き方・interceptors.request.use() / interceptors.response.use() の構文
// ============================================================
import axios from "axios";
import { useAuthStore } from "@/store/authStore";

// ▼ ドキュメントから貼る部分（axios の構文）
// axios.create() で「設定済みの axios インスタンス」を作る。
const api = axios.create({
  baseURL: "http://localhost:8080/api", // ← 自分で考える部分：バックエンドの URL
});

// ▼ ドキュメントから貼る部分（request interceptor の構文）
// リクエストを送る直前に config（送信設定のオブジェクト）を受け取って加工できる。
// return config を忘れるとリクエストが送られないので必須。
//
// config とは「このリクエストの設定が全部入ったオブジェクト」。
// たとえば api.post("/auth/login", { email: "...", password: "..." }) を呼ぶと、
// axios が以下のような config を自動で組み立てて interceptor に渡してくる。
//
// {
//   method: "POST",
//   url: "/auth/login",
//   baseURL: "http://localhost:8080/api",
//   headers: {
//     "Content-Type": "application/json",
//     // ← ここに Authorization を追加する
//   },
//   data: { email: "...", password: "..." }
// }
//
// interceptor は「全部 axios が組み立てた後、ネットワークに送る直前」に呼ばれる。
// だから config.headers.Authorization を追加するだけで全リクエストに JWT が付く。
api.interceptors.request.use((config) => {
  // ▼ 自分で考える部分：Zustand のストアからトークンを取り出す
  // useAuthStore.getState() はコンポーネントの外から Zustand の値を取り出す書き方。
  // （コンポーネントの中なら useAuthStore((s) => s.token) と書く）
  const token = useAuthStore.getState().token;

  if (token) {
    // ▼ 自分で考える部分：バックエンドの仕様に合わせた JWT の渡し方
    // Authorization ヘッダーに "Bearer eyJhb..." の形で付与する。
    // "Bearer " の後ろにスペースがあることに注意。
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config; // ← 加工後の config を返すことでリクエストが実際に送られる
});

// ▼ ドキュメントから貼る部分（response interceptor の構文）
// 第1引数：成功時（2xx）に呼ばれる関数
// 第2引数：失敗時（4xx・5xx）に呼ばれる関数
api.interceptors.response.use(
  (response) => response, // 成功時はそのまま返す
  (error) => {
    // ▼ 自分で考える部分：401 をどう扱うかの設計判断
    // 401 = サーバーが「このトークンは無効です」と返してきた状態。
    // トークン切れなのでログインページに強制リダイレクトする。
    if (error.response?.status === 401) {
      window.location.href = "/login";
    }
    // Promise.reject() でエラーをそのまま呼び出し元に投げる。
    // これがないと呼び出し元で catch できなくなる。
    return Promise.reject(error);
  }
);

export default api;
