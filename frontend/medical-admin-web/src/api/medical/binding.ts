import type { PageParams, UserPatientBinding } from './types';
import { get, post, put } from '@/utils/request';

export function listBindings(params?: PageParams) {
  return get<UserPatientBinding[]>('/medical/binding/list', params).json();
}

export function getBinding(bindingId: number) {
  return get<UserPatientBinding>(`/medical/binding/${bindingId}`).json();
}

export function addBinding(data: Partial<UserPatientBinding>) {
  return post('/medical/binding', data).json();
}

export function updateBinding(bindingId: number, data: Partial<UserPatientBinding>) {
  return put(`/medical/binding/${bindingId}`, data).json();
}
