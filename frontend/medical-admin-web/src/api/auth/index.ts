import type { CaptchaVO, EmailCodeDTO, LoginDTO, LoginVO, RegisterDTO } from './types';
import { get, post } from '@/utils/request';

export const login = (data: LoginDTO) => post<LoginVO>('/auth/login', data).json();

// 获取图形验证码
export const getCaptchaCode = () => get<CaptchaVO>('/auth/code').json();

// 邮箱验证码
export const emailCode = (data: EmailCodeDTO) => post('/resource/email/code', data).json();

// 注册账号
export const register = (data: RegisterDTO) => post('/auth/register', data).json();
