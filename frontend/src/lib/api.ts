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
