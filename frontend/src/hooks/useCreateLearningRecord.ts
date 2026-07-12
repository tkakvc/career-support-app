// 学習記録を1件作成し、一覧キャッシュを更新するフック
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { LearningRecord, LearningRecordCreateRequest } from "@/lib/api-types";
import api from "@/lib/api";

export function useCreateLearningRecord() {
  const queryClient = useQueryClient();

  return useMutation<LearningRecord, Error, LearningRecordCreateRequest>({
    mutationFn: (body) =>
      api.post<LearningRecord>("/learning-records", body).then((res) => res.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["learning-records"] });
    },
  });
}
