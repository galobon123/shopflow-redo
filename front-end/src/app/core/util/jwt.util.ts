export interface JwtPayload {
  sub: string;
  role: string;
  userId?: number;
  name?: string;
  /** HU-31 · False until the email is confirmed; only drives the UI, the backend checks the database. */
  emailVerified?: boolean;
  iat?: number;
  exp?: number;
}

export function decodeJwt(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const payloadBase64Url = parts[1];

    const payloadJson = base64UrlDecode(payloadBase64Url);

    return JSON.parse(payloadJson) as JwtPayload;
  } catch {
    return null;
  }
}

export function isTokenExpired(token: string): boolean {
  const payload = decodeJwt(token);
  if (!payload?.exp) return true;

  const expirationMs = payload.exp * 1000;
  return Date.now() >= expirationMs;
}

function base64UrlDecode(input: string): string {
  const base64 = input.replace(/-/g, '+').replace(/_/g, '/');

  const padded = base64.padEnd(base64.length + (4 - (base64.length % 4)) % 4, '=');

  const binaryString = atob(padded);

  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }

  return new TextDecoder().decode(bytes);
}