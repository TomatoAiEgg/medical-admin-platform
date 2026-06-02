import type { ClinicSlot, PageParams } from './types';
import { get, post, put } from '@/utils/request';

export function listSlots(params?: PageParams) {
  return get<ClinicSlot[]>('/medical/slot/list', params).json();
}

export function getSlot(slotId: number | string) {
  return get<ClinicSlot>(`/medical/slot/${slotId}`).json();
}

export function addSlot(data: Partial<ClinicSlot>) {
  return post('/medical/slot', data).json();
}

export function batchGenerateSlots(data: {
  departmentCode: string;
  doctorId: string;
  startDate: string;
  endDate: string;
  weekdays?: number[];
  startTime: string;
  endTime: string;
  intervalMinutes: number;
  capacity: number;
  registrationFee?: number;
  roomNo?: string;
  sourceType?: string;
  remarks?: string;
}) {
  return post<Record<string, number>>('/medical/slot/batchGenerate', data).json();
}

export function updateSlot(slotId: number | string, data: Partial<ClinicSlot>) {
  return put(`/medical/slot/${slotId}`, data).json();
}

export function changeSlotStatus(data: { slotId: number | string; status: string }) {
  return put('/medical/slot/changeStatus', data).json();
}

export function adjustSlotInventory(
  slotId: number | string,
  data: { capacity?: number; remainingSlots?: number; reason?: string },
) {
  return put(`/medical/slot/${slotId}/inventory`, data).json();
}

export function changeSlotOperationalStatus(
  slotId: number | string,
  data: { status: 'OPEN' | 'CLOSED' | 'SUSPENDED' | 'CANCELLED'; reason?: string },
) {
  return put(`/medical/slot/${slotId}/operationalStatus`, data).json();
}
