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
// 【面接で説明できるようにする】なぜ _hasHydrated（復元完了フラグ）が必要か
//   → persist の localStorage 復元はブラウザ上で非同期に行われる。
//     URLを直接開いた（他ページ経由でなくフルページロードした）瞬間は、
//     復元がまだ終わっておらず accessToken は初期値の null のまま。
//     このタイミングで「token が null だから未ログイン」と判定すると、
//     実際はログイン済みなのに /login へ誤って飛ばしてしまう。
//     _hasHydrated で「復元が終わるまでは判定しない」を表現する。
//
//   【噛み砕くと】localStorage は「ロッカー」、Zustand の state は「机の上」に例えられる。
//     ページを開いた瞬間、机の上はまず空っぽ（accessToken = null）でスタートする。
//     その直後に「ロッカーから書類（トークン）を取り出して机に置く」作業が走るが、
//     これはゼロ秒ではなく、ほんの一瞬だけ時間がかかる。
//       ①ページを開く          → accessToken = null（まだ何もない）
//       ②ロッカーから読み込み中 →（一瞬の空白）
//       ③復元完了              → accessToken = "実際のトークン"
//     ①と③の間、必ず一瞬「null に見える時間」がある。
//     ログイン画面から遷移したときはこの問題が起きない。ログイン処理が setAuth() で
//     直接机の上にトークンを置く（ロッカー経由で読み直さない）ため、①〜③のタイムラグが無いから。
//     一方 URL を直接叩く／リロードするとページが完全に作り直されるので、
//     必ず①〜③のタイムラグが発生し、運悪く②のタイミングで判定するとバグる。
//     実際に career-support-app で起きたバグ：/tags に直リンクした瞬間、②のnullを見て
//     「未ログイン」と誤判定され、ログイン済みのユーザーが /login に追い返されていた。
//     _hasHydrated は「③が終わるまでは①と②を区別せず、判定そのものを保留する」ためのスイッチ。
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
  _hasHydrated: boolean;      // localStorage からの復元が完了したか。復元前は認証判定に使わない

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

  setHasHydrated: (hasHydrated: boolean) => void;
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
      // ▼ サーバー側レンダリング・復元前の初期値は false。
      // localStorage が使えるのはブラウザ上だけなので、復元が起きるのは常にクライアント側。
      _hasHydrated: false,

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

      setHasHydrated: (hasHydrated) => set({ _hasHydrated: hasHydrated }),
    }),
    {
      // ▼ name：localStorage に保存するときのキー名
      // localStorage.getItem("auth-store") で取り出せる。
      // ブラウザの開発者ツール → Application → Local Storage で確認できる。
      name: "auth-store",

      // ▼ onRehydrateStorage：localStorage からの復元が完了した「直後」に呼ばれるコールバックを返す関数。
      // 返した関数の引数 state は「復元が終わった直後のストアの中身」。
      // ここで setHasHydrated(true) を呼ぶことで、「もう判定してよい」と各画面に伝える。
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    }
  )
);
