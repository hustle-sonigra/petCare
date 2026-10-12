import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';

export interface AuthResponse { token: string; ownerId: string; name: string; email: string; }
export interface SigninRequest { name: string; email: string; password: string; timezone: string; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient); private readonly router = inject(Router); private readonly key = 'pet-tracker-auth';
  login(email: string, password: string): Observable<AuthResponse> { return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(tap(response => this.store(response))); }
  signin(request: SigninRequest): Observable<AuthResponse> { return this.http.post<AuthResponse>('/api/auth/signin', request).pipe(tap(response => this.store(response))); }
  token(): string | null {
    if (typeof localStorage === 'undefined') return null;
    try { return JSON.parse(localStorage.getItem(this.key) ?? 'null')?.token ?? null; } catch { localStorage.removeItem(this.key); return null; }
  }
  logout(): void { localStorage.removeItem(this.key); this.router.navigate(['/login']); }
  clear(): void { if (typeof localStorage !== 'undefined') localStorage.removeItem(this.key); }
  private store(response: AuthResponse): void { localStorage.setItem(this.key, JSON.stringify(response)); }
}
