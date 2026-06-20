// 検索条件を受け取って学習記録一覧を取得するフック
import { useQuery } from "@tanstack/react-query";
// useQuery は「APIを叩いてデータを取得する」フック。
// 内部でローディング状態・エラー状態・キャッシュを管理してくれる。
// Vue 2 で mounted() + data() + try/catch で書いていたコードが useQuery 1行になる。

import type { LearningRecord, SearchCriteria } from "@/lib/api-types";
// LearningRecord：GET /api/learning-records のレスポンス1件の型。
// SearchCriteria：{ tag?, from?, to?, keyword? } の型。全プロパティが optional（?付き）。

import api from "@/lib/api";
// lib/api.ts で作った axios インスタンス。
// リクエスト時に Authorization: Bearer <token> を自動で付与してくれる。

export function useLearningRecords(criteria: SearchCriteria = {}) {
  // criteria のデフォルト値を {} にしている。
  // 引数なしで useLearningRecords() と呼んだとき、criteria = {} になり
  // クエリパラメータなしで GET /api/learning-records を叩く（= 全件取得）。

  return useQuery<LearningRecord[]>({
    // <LearningRecord[]> は「このuseQueryが返す data の型は LearningRecord の配列」という指定。
    // これを書くことで data の型が LearningRecord[] として推論される。
    // 書かないと data が unknown 型になってしまい、data.map(...) などが型エラーになる。

    queryKey: ["learning-records", criteria],
    // queryKey はキャッシュの識別子。配列で書く。
    // 1つ目の "learning-records"：「学習記録に関するキャッシュ」というグループ名。
    //   invalidateQueries({ queryKey: ["learning-records"] }) で
    //   このグループ全体のキャッシュを一括で無効化できる。
    // 2つ目の criteria：検索条件。criteria が変わると queryKey が変わる。
    //   { tag: "Java" } → ["learning-records", { tag: "Java" }]
    //   { tag: "Spring" } → ["learning-records", { tag: "Spring" }]
    //   queryKey が変わると対応するキャッシュがないので自動で API を叩き直す。
    //   Vue 2 で watch に書いていた「条件が変わったら再取得」が自動になる。

    queryFn: () =>
      api
        .get<LearningRecord[]>("/learning-records", { params: criteria })
        // api.get<LearningRecord[]>(...) の <LearningRecord[]> は
        // 「axiosのレスポンスの data プロパティの型」を指定している。
        // params: criteria を渡すと axios が自動でクエリパラメータに変換する。
        // 例：criteria = { tag: "Java", from: "2026-01-01" } のとき
        //   GET /api/learning-records?tag=Java&from=2026-01-01 になる。
        .then((res) => res.data),
    // axios のレスポンスは { data, status, headers, ... } という構造になっている。
    // .then((res) => res.data) で「レスポンスの中の data だけを取り出す」処理。
    // useQuery の data にはこの res.data（= LearningRecord[] の配列）が入る。
  });
  // useQuery が返すオブジェクトをそのまま return している。
  // 呼び出し側は以下のように分割代入して使う：
  //   const { data, isLoading, isError } = useLearningRecords({ tag: "Java" });
  //   data      → LearningRecord[] | undefined（取得中は undefined）
  //   isLoading → true（APIを叩いている最中） / false（完了後）
  //   isError   → false（成功） / true（失敗）
}
