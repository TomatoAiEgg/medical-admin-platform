import type { DoctorMonitorSummary, MedicalExceptionHandleLog, MedicalExceptionRecord, MedicalExceptionRule, MonitorMetric, PageParams, PatientMonitorSummary } from './types';
import { get, post, put } from '@/utils/request';

export function getMonitorDashboard() {
  return get<MonitorMetric>('/medical/monitor/dashboard').json();
}

export function getDoctorMonitor(doctorId: string) {
  return get<MonitorMetric>(`/medical/monitor/doctor/${doctorId}`).json();
}

export function getDoctorTrace(doctorId: string, params?: PageParams) {
  return get<Record<string, unknown>>(`/medical/monitor/doctor/${doctorId}/trace`, params).json();
}

export function listDoctorMonitor(params?: PageParams) {
  return get<DoctorMonitorSummary[]>('/medical/monitor/doctor/list', params).json();
}

export function getPatientMonitor(patientId: string) {
  return get<MonitorMetric>(`/medical/monitor/patient/${patientId}`).json();
}

export function getPatientTrace(patientId: string, params?: PageParams) {
  return get<Record<string, unknown>>(`/medical/monitor/patient/${patientId}/trace`, params).json();
}

export function listPatientMonitor(params?: PageParams) {
  return get<PatientMonitorSummary[]>('/medical/monitor/patient/list', params).json();
}

export function getTraceDetail(params: { traceId?: string; registrationId?: string }) {
  return get<Record<string, unknown>>('/medical/monitor/trace', params).json();
}

export function listExceptionRules(params?: Record<string, unknown>) {
  return get<MedicalExceptionRule[]>('/medical/exception/rule/list', params).json();
}

export function listExceptions(params?: Record<string, unknown>) {
  return get<MedicalExceptionRecord[]>('/medical/exception/list', params).json();
}

export function listExceptionHandleLogs(params?: Record<string, unknown>) {
  return get<MedicalExceptionHandleLog[]>('/medical/exception/handle/log/list', params).json();
}

export function scanExceptions() {
  return post<MonitorMetric>('/medical/exception/scan', {}).json();
}

export function handleException(data: { exceptionId: number; status: string; remark?: string }) {
  return put('/medical/exception/handle', data).json();
}
