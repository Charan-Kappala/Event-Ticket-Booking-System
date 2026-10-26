export interface User {
  id: number;
  email: string;
  role: 'USER' | 'ORGANIZER' | 'ADMIN';
  name: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}