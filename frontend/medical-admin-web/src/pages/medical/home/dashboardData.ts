import type { DoctorMonitorSummary, PatientMonitorSummary, RegistrationOrder } from '@/api/medical';
import { getMonitorDashboard, listDoctorMonitor, listPatientMonitor, listRegistrations } from '@/api/medical';
import { useUserStore } from '@/stores';

export interface MedicalDashboardSnapshot {
  metrics: Record<string, unknown>;
  orders: RegistrationOrder[];
  doctors: DoctorMonitorSummary[];
  patients: PatientMonitorSummary[];
}

const CACHE_TTL_MS = 3000;

let pendingRequest: Promise<MedicalDashboardSnapshot> | null = null;
let cachedSnapshot: { time: number; data: MedicalDashboardSnapshot } | null = null;

function pickData<T>(res: unknown, fallback: T): T {
  if (Array.isArray(res))
    return res as T;
  const data = res as { data?: T; rows?: T };
  return data?.data || data?.rows || fallback;
}

function pickRows<T>(res: unknown): T[] {
  if (Array.isArray(res))
    return res as T[];
  const data = res as { rows?: T[]; data?: T[] };
  return data?.rows || data?.data || [];
}

export function loadMedicalDashboardSnapshot(force = false) {
  const userStore = useUserStore();
  if (!userStore.token || userStore.token === 'undefined' || userStore.token === 'null') {
    const data = {
      metrics: {},
      orders: [],
      doctors: [],
      patients: [],
    };
    return Promise.resolve(data);
  }

  if (pendingRequest)
    return pendingRequest;

  if (!force && cachedSnapshot && Date.now() - cachedSnapshot.time < CACHE_TTL_MS)
    return Promise.resolve(cachedSnapshot.data);

  pendingRequest = getMonitorDashboard().then(async (dashboardRes) => {
    const [orderRes, doctorRes, patientRes] = await Promise.all([
      listRegistrations({ pageNum: 1, pageSize: 200 }),
      listDoctorMonitor({ pageNum: 1, pageSize: 100 }),
      listPatientMonitor({ pageNum: 1, pageSize: 100 }),
    ]);
    const data = {
      metrics: pickData<Record<string, unknown>>(dashboardRes, {}),
      orders: pickRows<RegistrationOrder>(orderRes),
      doctors: pickRows<DoctorMonitorSummary>(doctorRes),
      patients: pickRows<PatientMonitorSummary>(patientRes),
    };
    cachedSnapshot = { time: Date.now(), data };
    return data;
  }).finally(() => {
    pendingRequest = null;
  });

  return pendingRequest;
}
