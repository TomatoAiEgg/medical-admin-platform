export interface PageParams {
  pageNum?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export interface Department {
  department_code: string;
  department_name: string;
  category_code?: string;
  description?: string;
  online_enabled?: boolean;
  active?: boolean;
}

export interface Doctor {
  doctor_id: string;
  department_code: string;
  doctor_name: string;
  title_name?: string;
  speciality?: string;
  specialty?: string;
  online_enabled?: boolean;
  active?: boolean;
}

export interface ClinicSlot {
  slot_id: number;
  department_code: string;
  doctor_id: string;
  clinic_date: string;
  start_time: string;
  end_time?: string;
  status: string;
  capacity: number;
  remaining_slots: number;
  registration_fee?: number;
  room_no?: string;
}

export interface RegistrationOrder {
  registration_id: string;
  user_id?: string;
  patient_id?: string;
  slot_id?: number;
  department_code?: string;
  doctor_id?: string;
  clinic_date?: string;
  start_time?: string;
  status: string;
  created_at?: string;
}

export interface RegistrationCreateOptions {
  slots: ClinicSlot[];
  patients: Array<{
    patient_id: string;
    patient_name?: string;
    phone_masked?: string;
    verified_status?: string;
    active?: boolean;
  }>;
  users: Array<{
    user_id: string;
    display_name?: string;
    nickname?: string;
    phone_masked?: string;
    status?: string;
  }>;
  bindings?: UserPatientBinding[];
}

export interface PatientProfile {
  patient_id: string;
  patient_name?: string;
  id_type?: string;
  id_number_masked?: string;
  phone_masked?: string;
  active?: boolean;
  verified_status?: string;
  source_channel?: string;
  updated_at?: string;
}

export interface PlatformUser {
  user_id: string;
  open_id?: string;
  union_id?: string;
  nickname?: string;
  display_name?: string;
  status?: string;
  avatar_url?: string;
  source_channel?: string;
  phone_masked?: string;
  updated_at?: string;
}

export interface UserPatientBinding {
  binding_id?: number;
  user_id: string;
  patient_id: string;
  relation_code?: string;
  is_default?: boolean;
  active?: boolean;
  bound_at?: string;
  created_at?: string;
  updated_at?: string;
}

export interface MonitorMetric {
  [key: string]: string | number | boolean | null | MonitorMetric[] | Record<string, unknown>;
}

export interface DoctorMonitorSummary {
  doctor_id: string;
  doctor_name?: string;
  department_code?: string;
  registration_count: number;
  booked_count: number;
  rescheduled_count?: number;
  cancelled_count: number;
  expired_count?: number;
  completed_count: number;
  no_show_count: number;
  slot_count: number;
  slot_capacity: number;
  remaining_slots: number;
  utilization_rate: number;
}

export interface PatientMonitorSummary {
  patient_id: string;
  patient_name?: string;
  phone_masked?: string;
  verified_status?: string;
  registration_count: number;
  booked_count: number;
  rescheduled_count?: number;
  cancelled_count: number;
  expired_count?: number;
  completed_count: number;
  no_show_count: number;
  next_clinic_date?: string;
  duplicate_risk_count: number;
}

export interface MedicalExceptionRecord {
  exception_id: number;
  rule_code: string;
  exception_type: string;
  severity: string;
  registration_id?: string;
  user_id?: string;
  patient_id?: string;
  doctor_id?: string;
  department_code?: string;
  slot_id?: number;
  biz_date?: string;
  title: string;
  content?: string;
  evidence_json?: string | Record<string, unknown>;
  status: string;
  handled_by?: string;
  handled_at?: string;
  handle_remark?: string;
  detected_at?: string;
  created_at?: string;
  updated_at?: string;
}

export interface MedicalExceptionRule {
  rule_id: number;
  rule_code: string;
  rule_name: string;
  rule_type: string;
  severity: string;
  enabled: boolean;
  description?: string;
}

export interface MedicalExceptionHandleLog {
  log_id: number;
  exception_id: number;
  old_status?: string;
  new_status: string;
  handle_user_id?: string;
  handle_user_name?: string;
  remark?: string;
  created_at?: string;
}

export interface KnowledgeDocument {
  id: string;
  namespace: string;
  source_id?: string;
  source_name?: string;
  document_type?: string;
  title: string;
  status?: string;
  version?: string;
  metadata?: string | Record<string, unknown>;
  created_at?: string;
}

export interface KnowledgeNamespaceSummary {
  namespace: string;
  document_count?: number;
  active_count?: number;
  latest_updated_at?: string;
}

export interface KnowledgeChunk {
  id: string;
  document_id?: string;
  namespace: string;
  chunk_index?: number;
  chunk_type?: string;
  title?: string;
  content: string;
  metadata?: string | Record<string, unknown>;
  enabled?: boolean;
  embedding_model?: string;
  embedding_dimensions?: number;
}

export interface KnowledgeImportPayload {
  namespace: string;
  sourceId: string;
  sourceName?: string;
  documentType: string;
  title: string;
  content: string;
  version?: string;
  status?: string;
  embeddingModel?: string;
  chunkSize?: number;
  chunkOverlap?: number;
  metadata?: Record<string, unknown>;
}

export interface MetadataRevisionPayload {
  targetType: 'DOCUMENT' | 'CHUNK';
  targetId: string;
  metadata: Record<string, unknown>;
  changedBy?: string;
  changeReason?: string;
}

export interface KnowledgeRetrievalPayload {
  namespace: string;
  query: string;
  embeddingModel?: string;
  topK?: number;
  minScore?: number;
}
