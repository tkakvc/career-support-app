// 学習記録を1件作成し、一覧キャッシュを更新するフック
import { useMutation, useQueryClient } from "@tanstack/react-query";
// useMutation は「POST / PUT / DELETE のAPIを叩く」フック。
// useQuery はコンポーネントが表示されたタイミングで自動でAPIを叩くが、
// useMutation はボタン押下など「任意のタイミング」で叩くときに使う。

// useQueryClient は「アプリ全体で共有しているキャッシュの保管場所（QueryClient）を受け取る」フック。
// providers.tsx で new QueryClient() して全体に配っているものをここで受け取る。
// Vue 2 で言うと this.$store に相当するが、Zustand（状態管理）ではなくキャッシュ専用の入れ物。

import type { LearningRecord, LearningRecordCreateRequest } from "@/lib/api-types";
// LearningRecord：POST 成功時にサーバーが返すレスポンスの型。
// LearningRecordCreateRequest：POST のリクエストボディの型。
//   { date: string; content: string; duration: number; tagIds?: string[] }

import api from "@/lib/api";

export function useCreateLearningRecord() {
  const queryClient = useQueryClient();
  // QueryClient のインスタンスを受け取る。
  // onSuccess の中で queryClient.invalidateQueries() を呼ぶために必要。

  return useMutation<LearningRecord, Error, LearningRecordCreateRequest>({
    // useMutation の型引数は3つ：
    //   1つ目 LearningRecord              → mutationFn が返す値の型（= POSTのレスポンス）
    //   2つ目 Error                       → 失敗時のエラーの型
    //   3つ目 LearningRecordCreateRequest → mutate() に渡す引数の型（= POSTのリクエストボディ）
    // これを書くことで mutation.mutate(body) の body が
    // LearningRecordCreateRequest 型として推論される。
    // 型が合わない値を渡すとコンパイルエラーになる。

    mutationFn: (body) =>
      api.post<LearningRecord>("/learning-records", body).then((res) => res.data),
    // mutationFn は「mutation.mutate(body) を呼んだときに実行される関数」。
    // body は mutation.mutate(body) で渡した値がそのまま入ってくる。
    // api.post<LearningRecord>("/learning-records", body) で
    //   POST http://localhost:8080/api/learning-records にリクエストボディ body を送る。
    // .then((res) => res.data) でレスポンスの中の data（= 作成された LearningRecord）だけを取り出す。

    onSuccess: () => {
      // POST が成功したあとに自動で呼ばれる。
      // onSuccess は (data: LearningRecord) => void という形で引数を受け取れるが、
      // 今回は作成されたレコードの内容を使わないので引数を省略している。
      // 使う場合は onSuccess: (data) => { console.log(data.id) } のように書く。
      queryClient.invalidateQueries({ queryKey: ["learning-records"] });
      // invalidateQueries は「このキャッシュはもう古い」とマークする処理。
      // queryKey: ["learning-records"] を指定すると
      //   ["learning-records"]
      //   ["learning-records", { tag: "Java" }]
      //   ["learning-records", { from: "2026-01-01" }]
      // のように "learning-records" で始まる全キャッシュが対象になる。
      // マークされた瞬間、その時点で画面に表示中のコンポーネントが
      // 自動で GET /api/learning-records を叩き直して画面を更新する。
      // Vue 2 で POST 後に手動で dispatch('fetchRecords') を呼んでいたのが自動になる。
    },
  });
  // useMutation が返すオブジェクトをそのまま return している。
  // 呼び出し側は以下のように使う：
  //   const mutation = useCreateLearningRecord();
  //   mutation.mutate({ date: "2026-06-20", content: "Javaの勉強", duration: 60 });
  //   mutation.isPending → true（POSTを叩いている最中） / false（完了後）
  //   mutation.isError   → false（成功） / true（失敗）
}
