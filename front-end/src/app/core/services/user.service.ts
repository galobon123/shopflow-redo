import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SignInRequest, SignUpRequest, AuthResponse, LoginChallenge, AuthConfig } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/auth`;

  /** A session (200) or, from a browser that isn't trusted yet, a code to confirm (202, HU-33). */
  signIn(credentials: SignInRequest & { deviceToken?: string | null }): Observable<AuthResponse | LoginChallenge> {
    return this.http.post<AuthResponse | LoginChallenge>(`${this.apiUrl}/login`, credentials);
  }

  confirmCode(challengeId: string, code: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login/code`, { challengeId, code });
  }

  resendCode(challengeId: string): Observable<LoginChallenge> {
    return this.http.post<LoginChallenge>(`${this.apiUrl}/login/code/resend`, { challengeId });
  }

  signUp(data: SignUpRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, data);
  }

  signInWithGoogle(credential: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/google`, { credential });
  }

  verifyEmail(token: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/verify-email`, { token });
  }

  resendVerification(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/verify-email/resend`, {});
  }

  /** HU-09 · 204 whether the email has an account or not. */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/forgot`, { email });
  }

  resetPassword(token: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/reset`, { token, password });
  }

  config(): Observable<AuthConfig> {
    return this.http.get<AuthConfig>(`${this.apiUrl}/config`);
  }
}
