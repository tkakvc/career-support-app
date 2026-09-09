# ── 依存関係インストール段階 ────────────────────────────────────────────────
# package.json・package-lock.json だけを先にコピーして依存関係を入れる。
# ソースコード（src など）はまだコピーしない。
# こうしておくと、ソースコードだけを変更したとき、この段階のキャッシュが
# 再利用され、npm install をやり直さずに済む（ビルドが速くなる）。

FROM node:22-alpine AS deps
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci
# npm ci = package-lock.json の内容通りに厳密にインストールするコマンド。
# npm install と違い、lock ファイルと package.json が食い違うとエラーになるため、
# 本番ビルドで使う依存関係の再現性が保証される。

# ── ビルド段階 ──────────────────────────────────────────────────────────────
# 依存関係（deps段階の node_modules）とソースコードを合わせてビルドする。

FROM node:22-alpine AS builder
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN npm run build
# next.config.ts の output: "standalone" 設定により、
# .next/standalone に本番実行に必要な最小限のファイルだけが出力される。

# ── 実行段階 ──────────────────────────────────────────────────────────────
# ビルド段階で作った成果物だけをコピーして、ビルドツール一式は捨てる。
# イメージを小さくするための分割（backend/Dockerfile と同じ考え方）。

FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
# Next.js が本番モードとして動くようにする環境変数。

COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
# standalone モードの出力には静的ファイル（.next/static）と public フォルダが
# 自動では含まれないため、別途手動でコピーする必要がある。

EXPOSE 3000
# コンテナが 3000番ポートで待ち受けることをDocumentする指定（実際のポート公開は
# ECSのタスク定義側のポートマッピングで行う。EXPOSE自体はポートを開ける効果はない）。

ENV PORT=3000
CMD ["node", "server.js"]
# standalone モードでは、next start の代わりに自動生成された server.js を
# 直接 node で実行する。これが本番起動時のエントリーポイントになる。
