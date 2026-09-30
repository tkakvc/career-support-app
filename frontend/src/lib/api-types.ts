// ============================================================
// バックエンドの DTO クラスと対応する型定義。バックエンドが変わればここも変わる。
// なぜフロントエンドでも型定義を持つか
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
  createdAt: string;
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

// リフレッシュトークンはJSON本文には含まれない。サーバーがHttpOnly Cookieとして
// 直接ブラウザに保存するため、フロントのJavaScriptはそもそも値を受け取らない。
export interface AuthResponse {
  accessToken: string;
  expiresIn: number; // アクセストークンの有効秒数（900 = 15分）
}

// POST /api/auth/refresh のレスポンス（アクセストークンだけを新しく発行する）
export interface AccessTokenResponse {
  accessToken: string;
  expiresIn: number;
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

export interface ReferenceRequest {
  interest?: string;
  // 学習記録の詳細画面から「この記録について参考資料を作る」で生成する場合だけ渡す
  recordId?: string;
}

export interface AiReferenceLink {
  url: string;
  title?: string;
}

// 参考資料1件。GET /api/ai/references の配列要素、およびGET /api/ai/jobs/{jobId}のresult（DONE時）と同じ形
// tagId/tagNameは既存タグに一致した場合だけ入る。一致しなければ両方undefinedで、代わりに
// suggestedTagName（AIが提案しただけでまだ作られていないタグ名）が入る
export interface AiReference {
  id: string;
  interest?: string;
  summaryHtml: string;
  tagId?: string;
  tagName?: string;
  suggestedTagName?: string;
  links: AiReferenceLink[];
  createdAt: string;
}

// POST /api/ai/references のレスポンス。
// jobIdがあれば202（非同期で生成中）、無くmessageだけあれば200（学習記録0件などの即時応答）
export interface AiJobAcceptedResponse {
  jobId?: string;
  message?: string;
}

export type AiJobStatus = "PENDING" | "PROCESSING" | "DONE" | "FAILED";

// GET /api/ai/jobs/{jobId} のレスポンス。resultはstatusがDONEのときだけAiReference
export interface AiJobResponse {
  status: AiJobStatus;
  result?: AiReference;
  message?: string;
}

// GET /api/ai/references のレスポンス
export interface AiReferenceListResponse {
  references: AiReference[];
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateProfileRequest {
  displayName: string;
}

export interface UpdatePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface PasswordUpdateResponse {
  result: string;
}
