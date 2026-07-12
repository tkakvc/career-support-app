// 検索条件を受け取って学習記録一覧を取得するフック
import { useQuery } from "@tanstack/react-query";
import type { LearningRecord, SearchCriteria } from "@/lib/api-types";
import api from "@/lib/api";

export function useLearningRecords(criteria: SearchCriteria = {}) {
  return useQuery<LearningRecord[]>({
    queryKey: ["learning-records", criteria],
    queryFn: () =>
      api
        .get<LearningRecord[]>("/learning-records", { params: criteria })
        .then((res) => res.data),
  });
}
