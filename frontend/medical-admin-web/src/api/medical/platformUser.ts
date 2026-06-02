import type { PageParams, PlatformUser } from './types';
import { get, post, put } from '@/utils/request';

export function listPlatformUsers(params?: PageParams) {
  return get<PlatformUser[]>('/medical/platformUser/list', params).json();
}

export function getPlatformUser(userId: string) {
  return get<PlatformUser>(`/medical/platformUser/${userId}`).json();
}

export function addPlatformUser(data: Partial<PlatformUser>) {
  return post('/medical/platformUser', data).json();
}

export function updatePlatformUser(userId: string, data: Partial<PlatformUser>) {
  return put(`/medical/platformUser/${userId}`, data).json();
}
