import { Injectable } from '@angular/core';
import { AuthService } from '../auth/auth.service';

export const API_URL = 'http://localhost:8080';

/**
 * Every backend call goes through here. It plays the role of an HTTP interceptor
 * (this app uses fetch, not HttpClient, so Angular interceptors would never run):
 * - attaches "Authorization: Bearer <token>"
 * - on 401 (no/expired token) logs out and sends the user to /login
 * - adds an Idempotency-Key header when the caller passes one
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  // One key per user action (e.g. "book event 3, seats A1,A2"), kept until that action gets a definite answer
  private idempotencyKeys = new Map<string, string>();

  constructor(private auth: AuthService) {}

  async fetch(path: string, init: RequestInit = {}, idempotencyKey?: string): Promise<Response> {
    const headers = new Headers(init.headers);
    const token = this.auth.getToken();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    if (init.body && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }
    if (idempotencyKey) {
      headers.set('Idempotency-Key', idempotencyKey);
    }

    const response = await fetch(API_URL + path, { ...init, headers });
    if (response.status === 401) {
      this.auth.logout(); // clears the stored token and navigates to /login
    }
    return response;
  }

  /** Returns the key for this action, creating one the first time. A retry of the same action reuses it. */
  idempotencyKeyFor(action: string): string {
    let key = this.idempotencyKeys.get(action);
    if (!key) {
      key = crypto.randomUUID();
      this.idempotencyKeys.set(action, key);
    }
    return key;
  }

  /**
   * Call once the server has given a definite answer (success or a 4xx). After a network error
   * or a 5xx, don't call it: the next click is a retry and must send the same key.
   */
  finishAction(action: string): void {
    this.idempotencyKeys.delete(action);
  }

  /** The server's error message (from {"message": ...}), or a fallback. */
  async errorMessage(response: Response, fallback: string): Promise<string> {
    try {
      const body = await response.json();
      return body?.message || fallback;
    } catch {
      return fallback;
    }
  }
}
