// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Zustand を使うか（グローバル状態管理の理由）
//   → accessToken・userId・displayName はログイン後にアプリ全体で使う情報。
//     これを props で渡していくと「バケツリレー（prop drilling）」になり、
//     何層も深いコンポーネントにまで渡すのが大変になる。
//     Zustand を使えば、どのコンポーネントからでも useAuthStore() 1行で取り出せる。
//
// 【変更履歴】なぜ persist（localStorage保存）をやめたか
//   → 以前は accessToken・refreshToken の両方を persist で localStorage に保存していたが、
//     XSSが起きた場合にどちらもJavaScriptから読み取れてしまう問題があった。
//     対策として、refreshTokenはサーバーがHttpOnly Cookieとして発行する方式に変更した。
//     HttpOnly CookieはJavaScriptから中身を読めないため、このストアはrefreshTokenを一切持たない。
//     accessTokenも、persist経由でlocalStorageに置く理由が無くなった（後述）ため、
//     このストアは全体としてメモリ上（ページを閉じる・リロードすると消える）だけの状態になった。
//
// 【面接で説明できるようにする】ページ再読み込み時にログイン状態をどう復元するか
//   → 以前はlocalStorageに保存したaccessToken・refreshTokenをpersistがそのまま復元していた。
//     今はrefreshTokenがHttpOnly CookieでJavaScriptから見えないため、この方法が使えない。
//     代わりに、アプリ起動時（(main)/layout.tsx）にPOST /api/auth/refreshを呼び、
//     ブラウザが自動で送ってくれるCookieを使ってサーバー側で検証し、新しいaccessTokenを
//     もらい直す「サイレントリフレッシュ」という方式に変えた。
//     _hasCheckedAuth は、このサイレントリフレッシュの試行が終わったかどうかを表すフラグ。
//
//   【噛み砕くと】以前の _hasHydrated は「localStorageというロッカーから、ページを開いた瞬間に
//     書類（トークン）を取り出して机の上に置く」までの一瞬の空白を表すフラグだった。
//     今の _hasCheckedAuth は、ロッカーの代わりに「サーバーに問い合わせて（POST /auth/refresh）
//     書類を発行し直してもらう」までの空白を表す、役割としては同じスイッチ。
//     問い合わせが返ってくるまでは、本当に未ログインなのか・まだ結果待ちなのかを区別できないため、
//     判定そのものを保留する（詳しくは (main)/layout.tsx のコメント参照）。
// 【AI任せでOK】create<AuthState>(...) の構文
// ============================================================
import { create } from "zustand";

// ▼ AuthState：このストアが持つ「値」と「アクション」の型定義
// Vuex では state / actions / mutations が別で書いていたが、
// Zustand は全部この1つの interface にまとめて書く。
interface AuthState {
  // --- 値（Vuex の state に相当） ---
  accessToken: string | null;  // 短命JWT（15分）。毎リクエストの認証に使う。null = 未ログイン
  userId: string | null;      // ログイン中ユーザーの ID。null = 未ログイン
  displayName: string | null; // ログイン中ユーザーの表示名。null = 未ログイン
  email: string | null;       // ログイン中ユーザーのメールアドレス。パスワード変更後の再ログインに使う
  _hasCheckedAuth: boolean;    // 起動時のサイレントリフレッシュ（セッション復元）が終わったか

  // --- アクション（Vuex の mutations に相当） ---
  // Zustand に mutations という概念はなく、アクションから直接 set() で値を書き換える。
  setAuth: (accessToken: string, userId: string, displayName: string, email: string) => void;
  // ↑ setAuth は「引数を4つ受け取って、戻り値なし（void）の関数」という型。
  //   ログイン成功後・起動時のサイレントリフレッシュ成功後に呼ぶ。

  // ▼ setAccessToken：/refresh でアクセストークンだけを更新するとき用
  // userId・displayName は変えず、accessToken だけ書き換える。
  setAccessToken: (accessToken: string) => void;

  // ▼ setDisplayName：設定画面で表示名を変更した直後に呼ぶ
  // ヘッダーのアバター（displayNameの頭文字）をすぐ最新の値に反映させるための専用アクション。
  // accessToken 等まで含む setAuth を呼び直す必要はない。
  setDisplayName: (displayName: string) => void;

  clearAuth: () => void;
  // ↑ clearAuth は「引数なし・戻り値なし（void）の関数」という型。
  //   ログアウト時に呼ぶ。全ての値を null に戻す。

  setHasCheckedAuth: (hasCheckedAuth: boolean) => void;
}

// ▼ create<AuthState>(...) の構文について
// create<AuthState> の <AuthState> は「このストアの型は AuthState です」という TypeScript の指定。
// persist を使わないので create の後ろの括弧は1つだけでよい
// （persistを使っていた頃は create<AuthState>()(persist(...)) と括弧が2つだった）。
export const useAuthStore = create<AuthState>((set) => ({
  // --- 初期値（アプリ起動直後・未ログイン状態） ---
  accessToken: null,
  userId: null,
  displayName: null,
  email: null,
  // ▼ サイレントリフレッシュが終わるまではfalse。
  // (main)/layout.tsx がこのフラグを見て「まだ判定してよいタイミングではない」を表現する。
  _hasCheckedAuth: false,

  // ▼ setAuth：ログイン成功後・起動時のサイレントリフレッシュ成功後に呼ぶ
  setAuth: (accessToken, userId, displayName, email) =>
    set({ accessToken, userId, displayName, email }),

  // ▼ setAccessToken：/refresh 成功後に呼ぶ（userId等は変えない）
  setAccessToken: (accessToken) => set({ accessToken }),

  // ▼ setDisplayName：設定画面のプロフィール保存に成功した直後に呼ぶ
  setDisplayName: (displayName) => set({ displayName }),

  // ▼ clearAuth：ログアウト時に呼ぶ
  // 全ての値を null に戻す = 未ログイン状態に戻す。
  clearAuth: () =>
    set({ accessToken: null, userId: null, displayName: null, email: null }),

  setHasCheckedAuth: (hasCheckedAuth) => set({ _hasCheckedAuth: hasCheckedAuth }),
}));
