import type { EpPropMergeType } from 'element-plus/es/utils';

export type ElementTagType = EpPropMergeType<StringConstructor, 'success' | 'warning' | 'info' | 'primary' | 'danger', unknown>;

export interface StatusMeta {
  label: string;
  type: ElementTagType;
}

const registrationStatusMap: Record<string, StatusMeta> = {
  PENDING_CONFIRM: { label: '待确认', type: 'warning' },
  BOOKED: { label: '已预约', type: 'success' },
  RESCHEDULED: { label: '已改约', type: 'primary' },
  CANCELLED: { label: '已取消', type: 'info' },
  EXPIRED: { label: '已过期', type: 'info' },
  COMPLETED: { label: '已完成', type: 'success' },
  NO_SHOW: { label: '爽约', type: 'danger' },
};

const severityMap: Record<string, StatusMeta> = {
  INFO: { label: '提示', type: 'info' },
  WARN: { label: '警告', type: 'warning' },
  WARNING: { label: '警告', type: 'warning' },
  ERROR: { label: '错误', type: 'danger' },
  CRITICAL: { label: '严重', type: 'danger' },
};

const exceptionStatusMap: Record<string, StatusMeta> = {
  UNHANDLED: { label: '未处理', type: 'danger' },
  PROCESSING: { label: '处理中', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'primary' },
  IGNORED: { label: '已忽略', type: 'info' },
  RESOLVED: { label: '已解决', type: 'success' },
};

export function getRegistrationStatusMeta(value?: string): StatusMeta {
  if (!value)
    return { label: '-', type: 'info' };
  return registrationStatusMap[value] || { label: value, type: 'info' };
}

export function getSeverityMeta(value?: string): StatusMeta {
  if (!value)
    return { label: '-', type: 'info' };
  return severityMap[value] || { label: value, type: 'info' };
}

export function getExceptionStatusMeta(value?: string): StatusMeta {
  if (!value)
    return { label: '-', type: 'info' };
  return exceptionStatusMap[value] || { label: value, type: 'info' };
}

export function boolText(value: unknown) {
  return value === true || value === 1 || value === '1' ? '是' : '否';
}
