import type { Department, PageParams } from './types';
import { get, post, put } from '@/utils/request';

export function listDepartments(params?: PageParams) {
  return get<Department[]>('/medical/department/list', params).json();
}

export function getDepartment(departmentCode: string) {
  return get<Department>(`/medical/department/${departmentCode}`).json();
}

export function addDepartment(data: Partial<Department>) {
  return post('/medical/department', data).json();
}

export function updateDepartment(departmentCode: string, data: Partial<Department>) {
  return put(`/medical/department/${departmentCode}`, data).json();
}
