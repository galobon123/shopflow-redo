import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private readonly TOKEN_KEY = 'auth_token';
  /** HU-33 · Survives logout on purpose: it says "this browser is known", not "someone is signed in". */
  private readonly DEVICE_KEY = 'device_token';

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  saveToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  clear(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }

  getDeviceToken(): string | null {
    return localStorage.getItem(this.DEVICE_KEY);
  }

  saveDeviceToken(token: string): void {
    localStorage.setItem(this.DEVICE_KEY, token);
  }
}
