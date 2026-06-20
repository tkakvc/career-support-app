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
