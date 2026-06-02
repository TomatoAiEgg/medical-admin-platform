import type { ClinicSlot, Department, Doctor, PatientProfile, PlatformUser } from '@/api/medical';
import { listDepartments, listDoctors, listPatients, listPlatformUsers } from '@/api/medical';
import { displayValue, pickRows } from './format';

export const categoryOptions = [
  { label: '门诊科室', value: 'OUTPATIENT' },
  { label: '急诊科室', value: 'EMERGENCY' },
  { label: '医技科室', value: 'TECH' },
  { label: '住院科室', value: 'INPATIENT' },
  { label: '内科系统', value: 'INTERNAL' },
  { label: '外科系统', value: 'SURGERY' },
  { label: '专科门诊', value: 'SPECIALTY' },
];

export const sourceOptions = [
  { label: '后台录入', value: 'ADMIN' },
  { label: '后台挂号', value: 'MANUAL_ADMIN' },
  { label: '前台 H5', value: 'H5' },
  { label: '小程序', value: 'MINIAPP' },
  { label: 'AI 对话', value: 'AI_CHAT' },
  { label: '本地同步', value: 'LOCAL' },
];

export const relationOptions = [
  { label: '本人', value: 'SELF' },
  { label: '子女', value: 'CHILD' },
  { label: '父母', value: 'PARENT' },
  { label: '配偶', value: 'SPOUSE' },
  { label: '其他', value: 'OTHER' },
];

export function optionLabel(options: Array<{ label: string; value: unknown }>, value: unknown, fallback = '-') {
  return options.find(item => item.value === value)?.label || displayValue(value, fallback);
}

export function sourceLabel(value?: string) {
  return optionLabel(sourceOptions, value);
}

export function relationLabel(value?: string) {
  return optionLabel(relationOptions, value);
}

export function categoryLabel(value?: string) {
  return optionLabel(categoryOptions, value);
}

export function departmentName(code: string | undefined, departments: Department[]) {
  if (!code)
    return '-';
  return departments.find(item => item.department_code === code)?.department_name || '未匹配科室';
}

export function doctorName(id: string | undefined, doctors: Doctor[]) {
  if (!id)
    return '-';
  return doctors.find(item => item.doctor_id === id)?.doctor_name || '未匹配医生';
}

export function patientName(id: string | undefined, patients: PatientProfile[]) {
  if (!id)
    return '-';
  return patients.find(item => item.patient_id === id)?.patient_name || '未匹配就诊人';
}

export function platformUserName(id: string | undefined, users: PlatformUser[]) {
  if (!id)
    return '-';
  const user = users.find(item => item.user_id === id);
  return user?.display_name || user?.nickname || user?.phone_masked || '未匹配用户';
}

export function userOptionLabel(user: PlatformUser | RegistrationOptionUser) {
  return `${user.display_name || user.nickname || user.phone_masked || '未命名用户'}${user.phone_masked ? ` (${user.phone_masked})` : ''}`;
}

export function patientOptionLabel(patient: PatientProfile | RegistrationOptionPatient) {
  return `${patient.patient_name || '未命名就诊人'}${patient.phone_masked ? ` (${patient.phone_masked})` : ''}`;
}

export function doctorOptionLabel(doctor: Doctor, departments: Department[]) {
  const dept = departmentName(doctor.department_code, departments);
  return `${doctor.doctor_name}${dept !== '未匹配科室' ? ` - ${dept}` : ''}`;
}

export function slotOptionLabel(slot: ClinicSlot, departments: Department[], doctors: Doctor[]) {
  const dept = departmentName(slot.department_code, departments);
  const doctor = doctorName(slot.doctor_id, doctors);
  return `${dept} / ${doctor} / ${slot.clinic_date} ${slot.start_time}-${slot.end_time || ''} / 剩余 ${Number(slot.remaining_slots || 0)}`;
}

export async function loadDepartments() {
  return pickRows<Department>(await listDepartments({ pageNum: 1, pageSize: 500 }));
}

export async function loadDoctors() {
  return pickRows<Doctor>(await listDoctors({ pageNum: 1, pageSize: 500 }));
}

export async function loadPatients() {
  return pickRows<PatientProfile>(await listPatients({ pageNum: 1, pageSize: 500 }));
}

export async function loadPlatformUsers() {
  return pickRows<PlatformUser>(await listPlatformUsers({ pageNum: 1, pageSize: 500 }));
}

interface RegistrationOptionUser {
  user_id: string;
  display_name?: string;
  nickname?: string;
  phone_masked?: string;
}

interface RegistrationOptionPatient {
  patient_id: string;
  patient_name?: string;
  phone_masked?: string;
}
