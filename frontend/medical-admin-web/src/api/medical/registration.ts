import type { PageParams, RegistrationCreateOptions, RegistrationOrder } from './types';
import { get, post, put } from '@/utils/request';

export function listRegistrations(params?: PageParams) {
  return get<RegistrationOrder[]>('/medical/registration/list', params).json();
}

export function getRegistration(registrationId: string) {
  return get<RegistrationOrder>(`/medical/registration/${registrationId}`).json();
}

export function getRegistrationCreateOptions(params?: PageParams) {
  return get<RegistrationCreateOptions>('/medical/registration/createOptions', params).json();
}

export function addRegistration(data: {
  userId: string;
  patientId: string;
  slotId: number | string;
  sourceChannel?: string;
  externalRequestId?: string;
}) {
  return post<RegistrationOrder>('/medical/registration', data).json();
}

export function listRegistrationAudit(registrationId: string) {
  return get(`/medical/registration/${registrationId}/audit`).json();
}

export function listRegistrationInventoryAudit(registrationId: string) {
  return get(`/medical/registration/${registrationId}/inventoryAudit`).json();
}

export function getRegistrationTimeline(registrationId: string) {
  return get(`/medical/registration/${registrationId}/timeline`).json();
}

export function getRegistrationDetail(registrationId: string) {
  return get<Record<string, unknown>>(`/medical/registration/${registrationId}/detail`).json();
}

export function transitionRegistration(
  registrationId: string,
  data: { action: 'CONFIRM' | 'CANCEL' | 'COMPLETE' | 'NO_SHOW'; reason?: string },
) {
  return put(`/medical/registration/${registrationId}/transition`, data).json();
}

export function rescheduleRegistration(
  registrationId: string,
  data: { slotId: number | string; reason?: string },
) {
  return put(`/medical/registration/${registrationId}/reschedule`, data).json();
}

export function expireOverdueRegistrations(data?: { reason?: string }) {
  return put<Record<string, number>>('/medical/registration/expireOverdue', data || {}).json();
}
