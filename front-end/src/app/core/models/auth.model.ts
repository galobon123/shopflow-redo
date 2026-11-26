export type Role = 'ROLE_USER' | 'ROLE_ADMIN';

export interface SignInRequest {
  email: string;
  password: string;
}

export interface SignUpRequest {
  name: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  role: string;
  emailVerified: boolean;
  /** HU-33 · Only when this browser just became trusted: keep it to skip the email code next time. */
  deviceToken?: string;
}

/** HU-33 · Right password from a new browser: a code went to `sentTo` ("a***@mail.com"). */
export interface LoginChallenge {
  challengeId: string;
  sentTo: string;
}

export interface AuthConfig {
  /** Null when sign-in with Google is off (no GOOGLE_CLIENT_ID on the server). */
  googleClientId: string | null;
}
