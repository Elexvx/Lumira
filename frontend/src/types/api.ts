export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  requestId?: string;
  timestamp: string;
}

export interface CurrentUser {
  userId: number;
  username: string;
  displayName: string;
  permissions: string[];
}
