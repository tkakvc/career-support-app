// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Zustand を使うか（グローバル状態管理の理由）
//   → JWT トークン・userId・displayName はログイン後にアプリ全体で使う情報。
//     これを props で渡していくと「バケツリレー（prop drilling）」になり、
//     何層も深いコンポーネントにまで渡すのが大変になる。
//     Zustand を使えば、どのコンポーネントからでも useAuthStore() 1行で取り出せる。
// 【面接で説明できるようにする】なぜ persist で localStorage に保存するか
//   → persist なしだと、ページをリロードするたびに token が null に戻りログアウト状態になる。
//     localStorage に保存することで、ブラウザを閉じても再訪問時にトークンを復元できる。
// 【面接で説明できるようにする】なぜ accessToken と refreshToken を分けて持つか
//   → バックエンドがリフレッシュトークン方式（アクセストークン=15分の短命JWT、
//     リフレッシュトークン=14日・Redisで失効管理）に対応したため。
//     accessTokenは毎リクエストの認証に使い、refreshTokenはaccessToken切れ時の再発行にのみ使う。
// 【AI任せでOK】create<AuthState>()() の二重括弧の構文・persist の書き方
// ============================================================
import { create } from "zustand";
import { persist } from "zustand/middleware";
// persist は「ストアの定義を包むと、localStorage への保存・復元を自動でやってくれる関数」。
// persist がないと、ページリロードで token・userId・displayName が全部 null に戻る。

// ▼ AuthState：このストアが持つ「値」と「アクション」の型定義
// Vuex では state / actions / mutations が別で書いていたが、
// Zustand は全部この1つの interface にまとめて書く。
interface AuthState {
  // --- 値（Vuex の state に相当） ---
  accessToken: string | null;  // 短命JWT（15分）。毎リクエストの認証に使う。null = 未ログイン
  refreshToken: string | null; // 長命トークン（14日）。accessToken切れ時の再発行にのみ使う
  userId: string | null;      // ログイン中ユーザーの ID。null = 未ログイン
  displayName: string | null; // ログイン中ユーザーの表示名。null = 未ログイン

  // --- アクション（Vuex の mutations に相当） ---
  // Zustand に mutations という概念はなく、アクションから直接 set() で値を書き換える。
  setAuth: (accessToken: string, refreshToken: string, userId: string, displayName: string) => void;
  // ↑ setAuth は「引数を4つ受け取って、戻り値なし（void）の関数」という型。
  //   ログイン成功後に呼ぶ。accessToken・refreshToken・userId・displayName を一括でストアにセットする。

  // ▼ setAccessToken：/refresh でアクセストークンだけを更新するとき用
  // refreshToken・userId・displayName は変えず、accessToken だけ書き換える。
  setAccessToken: (accessToken: string) => void;

  clearAuth: () => void;
  // ↑ clearAuth は「引数なし・戻り値なし（void）の関数」という型。
  //   ログアウト時に呼ぶ。全ての値を null に戻す。
}

// ▼ create<AuthState>()(...) の構文について
// create<AuthState> の <AuthState> は「このストアの型は AuthState です」という TypeScript の指定。
// create の後ろに ()() と括弧が2つあるのは、persist を使うときの Zustand の書き方。
// persist を使わない場合は create<AuthState>((set) => ({...})) と括弧が1つ。
export const useAuthStore = create<AuthState>()(
  // ▼ persist(ストアの定義, オプション) の形で使う
  // persist が「ストアの値が変わるたびに localStorage に保存」「ページ起動時に localStorage から復元」をやってくれる。
  persist(
    // ▼ (set) => ({...}) がストアの本体
    // set は「ストアの値を書き換える関数」。set(...) を呼ばないと値は変わらない。
    // Vuex で言うと commit に近い。ただし mutation の名前を指定する必要はなく、直接値を渡す。
    (set) => ({
      // --- 初期値（アプリ起動直後・未ログイン状態） ---
      accessToken: null,
      refreshToken: null,
      userId: null,
      displayName: null,

      // ▼ setAuth：ログイン成功後に呼ぶ
      setAuth: (accessToken, refreshToken, userId, displayName) =>
        set({ accessToken, refreshToken, userId, displayName }),

      // ▼ setAccessToken：/refresh 成功後に呼ぶ（refreshToken等は変えない）
      setAccessToken: (accessToken) => set({ accessToken }),

      // ▼ clearAuth：ログアウト時に呼ぶ
      // 全ての値を null に戻す = 未ログイン状態に戻す。
      // localStorage からも自動で削除される（persist がやってくれる）。
      clearAuth: () =>
        set({ accessToken: null, refreshToken: null, userId: null, displayName: null }),
    }),
    {
      // ▼ name：localStorage に保存するときのキー名
      // localStorage.getItem("auth-store") で取り出せる。
      // ブラウザの開発者ツール → Application → Local Storage で確認できる。
      name: "auth-store",
    }
  )
);
