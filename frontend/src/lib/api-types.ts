// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】このファイルのインターフェース定義（フィールドの列挙）は覚えなくていい
//   → バックエンドの DTO クラスと対応する型定義。バックエンドが変わればここも変わる。
// 【面接で説明できるようにする】なぜフロントエンドでも型定義を持つか
//   → TypeScript の型チェックを API の境界まで効かせるため。
//     型なしで api.post() の戻り値を受け取ると、存在しないフィールドへのアクセスがコンパイル時に検出できない。
// ============================================================
export interface LearningRecord {
  id: string;
  userId: string;
  date: string;
  content: string;
  duration: number;
  tags: Tag[];
  createdAt: string;
}

export interface LearningRecordCreateRequest {
  date: string;
  content: string;
  duration: number;
  tagIds?: string[];
}

export interface LearningRecordUpdateRequest {
  date: string;
  content: string;
  duration: number;
  tagIds?: string[];
}

export interface SearchCriteria {
  tag?: string;
  from?: string;
  to?: string;
  keyword?: string;
}

export interface Tag {
  id: string;
  name: string;
  type: string;
  createdBy: string | null;
}

export interface TagCreateRequest {
  name: string;
}

export interface TagUpdateRequest {
  name: string;
}

export interface Attachment {
  id: string;
  learningRecordId: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface SuggestResponse {
  suggestions: Suggestion[];
  message?: string;
}

export interface Suggestion {
  title: string;
  reason: string;
}

export interface DecomposeRequest {
  goal: string;
}

export interface DecomposeResponse {
  tasks: string[];
}
