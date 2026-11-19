export interface UserResponse {
    id: number;
    createdAt: string;
    email: string;
    name: string;
    role : UserRole;
}

export interface SignUpRequest {
    name: string;
    email: string;
    password: string;
}

export interface SignInRequest extends Omit<SignUpRequest, 'name'> {}

export type UserRole = 'USER' | 'ADMIN';