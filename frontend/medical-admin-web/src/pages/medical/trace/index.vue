<script setup lang="ts">
import { getTraceDetail } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import StatusTag from '../_components/StatusTag.vue';
import { displayValue, pickData } from '../_utils/format';

type TraceRecord = Record<string, any>;

interface CallNode {
  name: string;
  desc: string;
  status?: string;
  latency?: string | number;
}

const loading = ref(false);
const query = reactive({
  keyword: '',
});
const detail = ref<TraceRecord>({});

const events = computed<TraceRecord[]>(() => detail.value.events || []);
const registrationAudits = computed<TraceRecord[]>(() => detail.value.registrationAudits || []);
const inventoryAudits = computed<TraceRecord[]>(() => detail.value.inventoryAudits || []);
const exceptions = computed<TraceRecord[]>(() => detail.value.exceptions || []);
const retrievalLogs = computed<TraceRecord[]>(() => detail.value.retrievalLogs || []);

const metrics = computed(() => [
  { title: '链路事件', value: events.value.length, desc: '业务与系统事件', tone: 'primary' as const, icon: 'Connection' },
  { title: '订单审计', value: registrationAudits.value.length, desc: '挂号订单变更', tone: 'success' as const, icon: 'Tickets' },
  { title: '库存审计', value: inventoryAudits.value.length, desc: '号源扣减与回补', tone: 'warning' as const, icon: 'Histogram' },
  { title: '异常记录', value: exceptions.value.length, desc: '规则命中与处理', tone: exceptions.value.length ? 'danger' as const : 'info' as const, icon: 'WarningFilled' },
  { title: 'RAG 检索', value: retrievalLogs.value.length, desc: '知识库召回日志', tone: 'info' as const, icon: 'Collection' },
]);

const callNodes = computed<CallNode[]>(() => {
  const raw = detail.value.nodes || detail.value.traceNodes || detail.value.callChain;
  if (Array.isArray(raw) && raw.length) {
    return raw.map((item: TraceRecord) => ({
      name: displayValue(item.name || item.nodeName || item.serviceName),
      desc: displayValue(item.desc || item.description || item.operation),
      status: item.status,
      latency: item.latency || item.latencyMs || item.durationMs,
    }));
  }
  return [];
});

function summaryValue(...keys: string[]) {
  for (const key of keys) {
    const value = detail.value[key];
    if (value !== undefined && value !== null && value !== '')
      return displayValue(value);
  }
  return '-';
}

function normalizedQuery() {
  const value = query.keyword.trim();
  if (!value)
    return {};
  if (value.toLowerCase().startsWith('trace') || value.includes('-'))
    return { traceId: value };
  return { registrationId: value };
}

function formatPayload(payload: unknown) {
  if (payload === undefined || payload === null || payload === '')
    return '{}';
  if (typeof payload === 'string')
    return payload;
  try {
    return JSON.stringify(payload, null, 2);
  }
  catch {
    return String(payload);
  }
}

function nodeTagType(status?: string) {
  const normalized = String(status || '').toUpperCase();
  if (['SUCCESS', 'OK', 'DONE', 'COMPLETED'].includes(normalized))
    return 'success';
  if (['FAILED', 'ERROR', 'EXCEPTION'].includes(normalized))
    return 'danger';
  if (['RUNNING', 'PROCESSING', 'PENDING'].includes(normalized))
    return 'warning';
  return 'info';
}

function boolLabel(value: unknown) {
  return value === true || value === 1 || value === '1' ? '是' : '否';
}

async function loadTrace() {
  const params = normalizedQuery();
  if (!Object.keys(params).length) {
    ElMessage.warning('请输入订单号或链路编号');
    return;
  }

  loading.value = true;
  try {
    detail.value = pickData(await getTraceDetail(params), {});
  }
  finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = '';
  detail.value = {};
}
</script>

<template>
  <div class="medical-page trace-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">链路追踪</h1>
        <p class="medical-page__desc">按订单号或链路编号聚合订单审计、库存审计、异常记录与 RAG 检索日志。</p>
      </div>
      <div class="medical-actions">
        <el-button :loading="loading" @click="loadTrace">刷新</el-button>
      </div>
    </div>

    <el-form class="medical-filter" :model="query" inline>
      <el-form-item label="查询关键字">
        <el-input v-model="query.keyword" clearable placeholder="输入订单号或链路编号" style="width: 300px" @keyup.enter="loadTrace" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="loadTrace">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="trace-metrics">
      <MetricCard v-for="item in metrics" :key="item.title" v-bind="item" />
    </div>

    <div class="trace-layout">
      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">调用链路</h2>
          <span class="medical-muted">仅展示真实调用节点</span>
        </div>
        <div class="medical-section__body">
          <div class="call-chain">
            <div v-for="(node, index) in callNodes" :key="`${node.name}-${index}`" class="call-node">
              <div class="call-node__index">{{ index + 1 }}</div>
              <div class="call-node__content">
                <div class="call-node__top">
                  <strong>{{ node.name }}</strong>
                  <el-tag size="small" :type="nodeTagType(node.status)">{{ displayValue(node.status, 'IDLE') }}</el-tag>
                </div>
                <p>{{ node.desc }}</p>
                <span v-if="node.latency" class="medical-muted medical-mono">{{ node.latency }} ms</span>
              </div>
            </div>
            <el-empty v-if="!callNodes.length && !loading" description="暂无真实调用节点" :image-size="80" />
          </div>
        </div>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">链路摘要</h2>
        </div>
        <div class="medical-section__body">
          <dl class="trace-summary">
            <div>
              <dt>订单号</dt>
              <dd>{{ summaryValue('registrationId', 'registration_id') }}</dd>
            </div>
            <div>
              <dt>患者</dt>
              <dd>{{ summaryValue('patientName', 'patient_name') }}</dd>
            </div>
            <div>
              <dt>科室</dt>
              <dd>{{ summaryValue('departmentName', 'department_name') }}</dd>
            </div>
            <div>
              <dt>医生</dt>
              <dd>{{ summaryValue('doctorName', 'doctor_name') }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ summaryValue('status') }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ summaryValue('createdAt', 'created_at') }}</dd>
            </div>
          </dl>
        </div>
      </section>
    </div>

    <section class="medical-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">链路时间线</h2>
        <span class="medical-muted">共 {{ events.length }} 条</span>
      </div>
      <div class="medical-section__body">
        <el-empty v-if="!events.length && !loading" description="暂无链路事件" />
        <el-timeline v-else v-loading="loading" class="trace-timeline">
          <el-timeline-item
            v-for="item in events"
            :key="`${item.type}-${item.eventTime}-${item.title}`"
            :timestamp="String(item.eventTime || item.createdAt || '')"
            placement="top"
          >
            <div class="trace-event">
              <div class="trace-event__title">
                <el-tag size="small" effect="light">{{ displayValue(item.type || item.eventType) }}</el-tag>
                <strong>{{ displayValue(item.title || item.name || item.operation) }}</strong>
                <span class="medical-muted">{{ displayValue(item.serviceName || item.source) }}</span>
              </div>
              <pre class="medical-json">{{ formatPayload(item.payload || item.detail || item.data) }}</pre>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </section>

    <section class="medical-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">审计与检索明细</h2>
      </div>
      <el-tabs class="trace-tabs">
        <el-tab-pane label="订单审计">
          <el-table v-loading="loading" :data="registrationAudits" class="medical-table" table-layout="fixed">
            <el-table-column prop="operation_type" label="操作" width="120" />
            <el-table-column label="成功" width="90">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ boolLabel(row.success) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="240" show-overflow-tooltip />
            <el-table-column prop="created_at" label="时间" min-width="170" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="库存审计">
          <el-table v-loading="loading" :data="inventoryAudits" class="medical-table" table-layout="fixed">
            <el-table-column prop="operation_type" label="操作" width="120" />
            <el-table-column prop="remaining_before" label="变更前" width="90" />
            <el-table-column prop="remaining_after" label="变更后" width="90" />
            <el-table-column prop="created_at" label="时间" min-width="170" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="异常记录">
          <el-table v-loading="loading" :data="exceptions" class="medical-table" table-layout="fixed">
            <el-table-column prop="rule_code" label="规则" min-width="160" show-overflow-tooltip />
            <el-table-column label="级别" width="110">
              <template #default="{ row }">
                <StatusTag :value="row.severity" kind="severity" />
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <StatusTag :value="row.status" kind="exception" />
              </template>
            </el-table-column>
            <el-table-column prop="detected_at" label="发现时间" min-width="170" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="RAG 检索">
          <el-table v-loading="loading" :data="retrievalLogs" class="medical-table" table-layout="fixed">
            <el-table-column prop="namespace" label="命名空间" min-width="150" />
            <el-table-column prop="query_text" label="问题" min-width="260" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="hit_count" label="命中" width="90" />
            <el-table-column prop="latency_ms" label="耗时ms" width="110" />
            <el-table-column prop="created_at" label="时间" min-width="170" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped lang="scss">
.trace-page {
  display: grid;
  gap: 12px;
}

.trace-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.trace-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 0.8fr);
  gap: 12px;
}

.call-chain {
  display: grid;
  gap: 10px;
}

.call-node {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 10px;
  align-items: flex-start;
}

.call-node__index {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  font-weight: 600;
  color: var(--medical-primary);
  background: var(--medical-primary-soft);
  border: 1px solid rgba(0, 103, 103, 0.18);
  border-radius: 50%;
}

.call-node__content {
  min-width: 0;
  padding: 10px 12px;
  background: var(--medical-surface-soft);
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius);
}

.call-node__top {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.call-node__content p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--medical-text-secondary);
}

.trace-summary {
  display: grid;
  gap: 12px;
  margin: 0;
}

.trace-summary div {
  display: grid;
  gap: 4px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--medical-border);
}

.trace-summary dt {
  font-size: 12px;
  color: var(--medical-text-muted);
}

.trace-summary dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 14px;
  color: var(--medical-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-timeline {
  padding-right: 8px;
}

.trace-event {
  padding: 12px;
  background: var(--medical-surface);
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius);
}

.trace-event__title {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.trace-event__title strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-tabs {
  padding: 0 14px 14px;
}

@media (max-width: 1400px) {
  .trace-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .trace-layout {
    grid-template-columns: 1fr;
  }

  .trace-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .trace-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
