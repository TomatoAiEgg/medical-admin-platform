import type { PageParams, PatientProfile } from './types';
import { get, post, put } from '@/utils/request';

export function listPatients(params?: PageParams) {
  return get<PatientProfile[]>('/medical/patient/list', params).json();
}

export function getPatient(patientId: string) {
  return get<PatientProfile>(`/medical/patient/${patientId}`).json();
}

export function addPatient(data: Partial<PatientProfile>) {
  return post('/medical/patient', data).json();
}

export function updatePatient(patientId: string, data: Partial<PatientProfile>) {
  return put(`/medical/patient/${patientId}`, data).json();
}
