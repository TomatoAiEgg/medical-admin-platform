import type { Doctor, PageParams } from './types';
import { get, post, put } from '@/utils/request';

export function listDoctors(params?: PageParams) {
  return get<Doctor[]>('/medical/doctor/list', params).json();
}

export function getDoctor(doctorId: string) {
  return get<Doctor>(`/medical/doctor/${doctorId}`).json();
}

export function addDoctor(data: Partial<Doctor>) {
  return post('/medical/doctor', data).json();
}

export function updateDoctor(doctorId: string, data: Partial<Doctor>) {
  return put(`/medical/doctor/${doctorId}`, data).json();
}
