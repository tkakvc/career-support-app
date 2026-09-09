import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // standalone = 本番実行に必要な最小限のファイル（依存パッケージも含む）だけを
  // .next/standalone に出力するモード。Dockerイメージに node_modules を
  // まるごとコピーしなくて済むため、イメージサイズを大幅に減らせる。
  output: "standalone",
};

export default nextConfig;
