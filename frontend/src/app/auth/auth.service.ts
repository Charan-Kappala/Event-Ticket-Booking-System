import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Router } from '@angular/router';

interface User {
  id: number;
  email: string;
  role: string;
  name?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(false);
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  
  isAuthenticated$ = this.isAuthenticatedSubject.asObservable();
  currentUser$ = this.currentUserSubject.asObservable();
  private apiUrl = 'http://localhost:8080/api';

  constructor(private router: Router) {
    this.checkAuthStatus();
  }

  // localStorage doesn't exist during server-side rendering
  private get storage(): Storage | null {
    return typeof localStorage === 'undefined' ? null : localStorage;
  }

  private checkAuthStatus() {
    const token = this.storage?.getItem('token');
    const user = this.storage?.getItem('user');
    if (token && user) {
      this.isAuthenticatedSubject.next(true);
      this.currentUserSubject.next(JSON.parse(user));
    }
  }

  /** Logs in and stores the JWT. Returns the user, or null if the email/password is wrong. */
  async login(email: string, password: string): Promise<User | null> {
    const response = await fetch(`${this.apiUrl}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    if (response.status === 401) {
      return null;
    }
    if (!response.ok) {
      throw new Error('Login failed');
    }
    const data = await response.json();
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(data.user));
    localStorage.setItem('userId', data.user.id.toString());
    this.isAuthenticatedSubject.next(true);
    this.currentUserSubject.next(data.user);
    return data.user;
  }

  logout(): void {
    this.storage?.removeItem('token');
    this.storage?.removeItem('user');
    this.storage?.removeItem('userId');
    this.isAuthenticatedSubject.next(false);
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  isAdmin(): boolean {
    const user = this.getCurrentUser();
    return user?.role === 'ADMIN';
  }

  isOrganizer(): boolean {
    const user = this.getCurrentUser();
    return user?.role === 'ORGANIZER';
  }

  isAuthenticated(): boolean {
    return this.isAuthenticatedSubject.value;
  }

  getToken(): string | null {
    return this.storage?.getItem('token') ?? null;
  }
}