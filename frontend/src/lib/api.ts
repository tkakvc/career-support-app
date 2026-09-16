// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ axios に interceptor を使うか
//   → 全リクエストに共通処理（JWT の付与）と全レスポンスに共通処理（401 時のリダイレクト）を
//     1箇所にまとめるため。interceptor なしだと、API を呼ぶ全箇所で
//     Authorization ヘッダーを手書きしないといけなくなる。
// 【面接で説明できるようにする】なぜ 401 で「まず /refresh を試す」のか
//   → アクセストークンは15分で失効する短命JWT。15分ごとに強制ログアウトされるとUXが悪い。
//     401が返ってきたら、まずリフレッシュトークン（14日・Redisで失効管理）で
//     新しいアクセストークンを取り直し、失敗した元のリクエストをもう一度送り直す。
//     リフレッシュ自体も失敗した（リフレッシュトークンも無効）場合だけログインページに飛ばす。
//
// 【変更履歴】なぜ withCredentials: true が要るか
//   → リフレッシュトークンはHttpOnly Cookieとしてサーバーが発行する方式に変えた。
//     axiosはデフォルトではCookieを送らないので、withCredentials: true を付けて
//     「このオリジン宛のリクエストにはCookieも一緒に送ってよい」と明示する必要がある。
// 【AI任せでOK】axios.create() の書き方・interceptors.request.use() / interceptors.response.use() の構文
// ============================================================
import axios from "axios";
import { useAuthStore } from "@/store/authStore";
import { AccessTokenResponse } from "@/lib/api-types";

// ▼ ドキュメントから貼る部分（axios の構文）
// axios.create() で「設定済みの axios インスタンス」を作る。
const api = axios.create({
  baseURL: "http://localhost:8080/api", // ← 自分で考える部分：バックエンドの URL
  withCredentials: true, // リフレッシュトークンのCookieを送受信するために必要
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
  // ▼ 自分で考える部分：Zustand のストアからアクセストークンを取り出す
  // useAuthStore.getState() はコンポーネントの外から Zustand の値を取り出す書き方。
  // （コンポーネントの中なら useAuthStore((s) => s.accessToken) と書く）
  const accessToken = useAuthStore.getState().accessToken;

  if (accessToken) {
    // ▼ 自分で考える部分：バックエンドの仕様に合わせた JWT の渡し方
    // Authorization ヘッダーに "Bearer eyJhb..." の形で付与する。
    // "Bearer " の後ろにスペースがあることに注意。
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config; // ← 加工後の config を返すことでリクエストが実際に送られる
});

// ▼ 401時にリフレッシュを試みるための「専用axios」
// api（上のインスタンス）で /auth/refresh を呼ぶと、request interceptorが古い
// アクセストークンをまた付けてしまい、response interceptorにも捕まってループしうる。
// なので /auth/refresh 用は interceptor を通さない素の axios を使う。
// withCredentials: true はここでも必要（リフレッシュトークンのCookieを送るため）。
const refreshClient = axios.create({
  baseURL: "http://localhost:8080/api",
  withCredentials: true,
});

// ▼ 同時に複数のAPIが401になったとき、/refresh を複数回呼ばないようにする仕組み。
// 1回目の401でリフレッシュ処理を始めたら、そのPromiseを使い回す。
let refreshingPromise: Promise<string> | null = null;

// ▼ export する理由：(main)/layout.tsx がアプリ起動時の「サイレントリフレッシュ」
// （ページを開いた瞬間にCookieでログイン状態を復元する処理）でもこの関数を再利用するため。
export async function refreshAccessToken(): Promise<string> {
  if (!refreshingPromise) {
    refreshingPromise = (async () => {
      // リフレッシュトークンはHttpOnly Cookieとしてブラウザが自動で送ってくれるため、
      // ここでは何も読み出さず・リクエストボディにも詰めない。
      // POST /api/auth/refresh → { accessToken, expiresIn }
      const res = await refreshClient.post<AccessTokenResponse>("/auth/refresh");
      useAuthStore.getState().setAccessToken(res.data.accessToken);
      return res.data.accessToken;
    })().finally(() => {
      // 完了したら（成功・失敗どちらでも）次の401でまた新しくリフレッシュできるようにリセット
      refreshingPromise = null;
    });
  }
  return refreshingPromise;
}

// ▼ ドキュメントから貼る部分（response interceptor の構文）
// 第1引数：成功時（2xx）に呼ばれる関数
// 第2引数：失敗時（4xx・5xx）に呼ばれる関数
api.interceptors.response.use(
  (response) => response, // 成功時はそのまま返す
  async (error) => {
    const originalRequest = error.config;

    // ▼ 自分で考える部分：401 をどう扱うかの設計判断
    // 401 = サーバーが「このアクセストークンは無効です（期限切れ等）」と返してきた状態。
    // ・まだリトライしていないリクエストなら、/refresh で新しいアクセストークンを取り直して
    //   同じリクエストをもう一度送る（_retry フラグで無限ループを防ぐ）。
    // ・/refresh 自体が失敗した（リフレッシュトークンも無効）ら、ログインページに強制誘導する。
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const newAccessToken = await refreshAccessToken();
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest); // 元のリクエストを新しいトークンで送り直す
      } catch {
        useAuthStore.getState().clearAuth();
        window.location.href = "/login";
        return Promise.reject(error);
      }
    }

    // Promise.reject() でエラーをそのまま呼び出し元に投げる。
    // これがないと呼び出し元で catch できなくなる。
    return Promise.reject(error);
  }
);

export default api;
