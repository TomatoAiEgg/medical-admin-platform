<script setup lang="ts">
import type { RegistrationOrder } from '@/api/medical';
import { getRegistrationDetail, getRegistrationTimeline, getTraceDetail, listRegistrations } from '@/api/medical';
import StatusTag from '../_components/StatusTag.vue';
import { displayValue, pickData, pickRows } from '../_utils/format';

const loading = ref(false);
const keyword = ref('');
const traceId = ref('');
const registrationId = ref('');
const orderRows = ref<RegistrationOrder[]>([]);
const selectedOrder = ref<RegistrationOrder | null>(null);
const detailData = ref<Record<string, any>>({});
const timelineRows = ref<Record<string, unknown>[]>([]);
const traceData = ref<Record<string, any>>({});

const traceNodes = computed(() => {
  const raw = traceData.value.nodes || traceData.value.traceNodes;
  if (Array.isArray(raw))
    return raw;
  return [];
});

const logRows = computed(() => {
  const raw = traceData.value.logs || traceData.value.traceLogs;
  if (Array.isArray(raw))
    return raw;
  return [];
});

function normalizeSearch() {
  const value = keyword.value.trim();
  traceId.value = '';
  registrationId.value = '';
  if (!value)
    return;
  if (value.toLowerCase().startsWith('trace') || value.includes('-'))
    traceId.value = value;
  else
    registrationId.value = value;
}

async function searchTrace() {
  normalizeSearch();
  loading.value = true;
  try {
    if (registrationId.value) {
      const [listRes, detailRes, timelineRes, traceRes] = await Promise.all([
        listRegistrations({ pageNum: 1, pageSize: 10, registrationId: registrationId.value }),
        getRegistrationDetail(registrationId.value),
        getRegistrationTimeline(registrationId.value),
        getTraceDetail({ registrationId: registrationId.value }),
      ]);
      orderRows.value = pickRows<RegistrationOrder>(listRes);
      selectedOrder.value = orderRows.value[0] || null;
      detailData.value = pickData<Record<string, any>>(detailRes, {});
      timelineRows.value = pickRows<Record<string, unknown>>(timelineRes);
      traceData.value = pickData<Record<string, any>>(traceRes, {});
    }
    else {
      const traceRes = await getTraceDetail({ traceId: traceId.value || keyword.value.trim() });
      traceData.value = pickData<Record<string, any>>(traceRes, {});
      orderRows.value = [];
      selectedOrder.value = null;
      detailData.value = {};
      timelineRows.value = [];
    }
  }
  finally {
    loading.value = false;
  }
}

async function openOrder(row: RegistrationOrder) {
  keyword.value = row.registration_id;
  await searchTrace();
}

function orderPatient(row: RegistrationOrder) {
  return detailData.value.patient?.patient_name || row.patient_id || '未匹配患者';
}

function orderDepartment(row: RegistrationOrder) {
  return detailData.value.department?.department_name || row.department_code || '未匹配科室';
}

onMounted(async () => {
  const res = await listRegistrations({ pageNum: 1, pageSize: 8 });
  orderRows.value = pickRows<RegistrationOrder>(res);
  selectedOrder.value = orderRows.value[0] || null;
  if (selectedOrder.value) {
    keyword.value = selectedOrder.value.registration_id;
    await searchTrace();
  }
});
</script>

<template>
  <div v-loading="loading" class="medical-page trace-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">订单追踪</h1>
        <p class="medical-page__desc">按订单号或链路编号排查挂号流程，查看状态时间线、接口日志和调用节点。</p>
      </div>
      <div class="trace-search">
        <el-input v-model="keyword" placeholder="输入订单号或链路编号" clearable @keyup.enter="searchTrace">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="searchTrace">查询</el-button>
      </div>
    </div>

    <div class="trace-layout">
      <section class="medical-section order-list">
        <div class="medical-section__header">
          <h2 class="medical-section__title">最近订单</h2>
        </div>
        <div class="order-list__body">
          <button
            v-for="item in orderRows"
            :key="item.registration_id"
            type="button"
            class="order-card"
            :class="{ active: item.registration_id === selectedOrder?.registration_id }"
            @click="openOrder(item)"
          >
            <strong>{{ orderPatient(item) }}</strong>
            <span>{{ orderDepartment(item) }} / {{ displayValue(item.clinic_date) }}</span>
            <StatusTag :value="item.status" />
          </button>
          <el-empty v-if="!orderRows.length" description="暂无订单" :image-size="72" />
        </div>
      </section>

      <section class="medical-section order-summary">
        <div class="medical-section__header">
          <h2 class="medical-section__title">订单摘要</h2>
          <StatusTag :value="detailData.order?.status || selectedOrder?.status" />
        </div>
        <div class="medical-section__body">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="患者">{{ displayValue(detailData.patient?.patient_name || selectedOrder?.patient_id) }}</el-descriptions-item>
            <el-descriptions-item label="平台用户">{{ displayValue(detailData.user?.display_name || detailData.user?.nickname || selectedOrder?.user_id) }}</el-descriptions-item>
            <el-descriptions-item label="科室">{{ displayValue(detailData.department?.department_name || selectedOrder?.department_code) }}</el-descriptions-item>
            <el-descriptions-item label="医生">{{ displayValue(detailData.doctor?.doctor_name || selectedOrder?.doctor_id) }}</el-descriptions-item>
            <el-descriptions-item label="就诊日期">{{ displayValue(detailData.order?.clinic_date || selectedOrder?.clinic_date) }}</el-descriptions-item>
            <el-descriptions-item label="就诊时段">{{ displayValue(detailData.order?.start_time || selectedOrder?.start_time) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </section>

      <section class="medical-section timeline-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">状态时间线</h2>
        </div>
        <div class="medical-section__body">
          <el-timeline>
            <el-timeline-item
              v-for="item in timelineRows"
              :key="`${item.event_type}-${item.event_time}-${item.status}`"
              :timestamp="String(item.event_time || '')"
              placement="top"
            >
              <div class="trace-timeline-card">
                <strong>{{ displayValue(item.title) }}</strong>
                <div>
                  <el-tag size="small">{{ displayValue(item.event_type) }}</el-tag>
                  <StatusTag :value="String(item.status || '')" />
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!timelineRows.length" description="暂无订单时间线" :image-size="80" />
        </div>
      </section>

      <section class="medical-section call-chain-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">调用链路</h2>
          <span class="medical-muted">仅展示后端返回的真实调用节点</span>
        </div>
        <div class="call-chain">
          <div v-for="node in traceNodes" :key="node.name" class="call-node" :class="{ error: node.status === 'ERROR' || node.status === 'FAILED' }">
            <div class="call-node__icon">
              <el-icon><Connection /></el-icon>
            </div>
            <strong>{{ node.name }}</strong>
            <span>{{ node.latency || node.latencyMs || '-' }}</span>
            <small>{{ node.summary || node.status || '-' }}</small>
          </div>
          <el-empty v-if="!traceNodes.length" description="暂无真实调用节点" :image-size="80" />
        </div>
      </section>

      <section class="medical-section log-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">日志详情</h2>
        </div>
        <el-table :data="logRows" class="medical-table" height="280" table-layout="fixed" empty-text="暂无链路日志">
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="name" label="节点" width="140" />
          <el-table-column prop="latency" label="耗时" width="100" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" min-width="170" />
        </el-table>
        <div v-if="!logRows.length" class="medical-json">{{ JSON.stringify(traceData, null, 2) }}</div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.trace-search {
  display: flex;
  gap: 8px;
  width: 420px;
}

.trace-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 12px;
}

.order-list {
  grid-row: span 4;
}

.order-list__body {
  display: grid;
  gap: 8px;
  padding: 10px;
}

.order-card {
  display: grid;
  gap: 5px;
  padding: 10px;
  text-align: left;
  cursor: pointer;
  background: var(--medical-surface);
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius);
}

.order-card:hover,
.order-card.active {
  border-color: var(--medical-primary);
  box-shadow: inset 2px 0 0 var(--medical-primary);
}

.order-card strong {
  color: var(--medical-text);
}

.order-card span {
  color: var(--medical-text-muted);
}

.call-chain {
  display: grid;
  grid-template-columns: repeat(7, minmax(110px, 1fr));
  gap: 8px;
  padding: 14px;
  overflow-x: auto;
}

.call-node {
  min-width: 112px;
  padding: 10px;
  text-align: center;
  background: var(--medical-surface-soft);
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius);
}

.call-node.error {
  border-color: var(--medical-danger);
}

.call-node__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-bottom: 6px;
  color: var(--medical-primary);
  background: var(--medical-primary-soft);
  border-radius: 50%;
}

.call-node strong,
.call-node span,
.call-node small {
  display: block;
}

.call-node span,
.call-node small {
  margin-top: 4px;
  color: var(--medical-text-muted);
}

.trace-timeline-card {
  padding: 10px 12px;
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius);
}

.trace-timeline-card > div {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}

.log-section {
  grid-column: 2;
}

.medical-json {
  margin: 12px;
  max-height: 220px;
}

@media (max-width: 1200px) {
  .trace-layout {
    grid-template-columns: 1fr;
  }

  .order-list,
  .log-section {
    grid-column: auto;
    grid-row: auto;
  }
}
</style>
