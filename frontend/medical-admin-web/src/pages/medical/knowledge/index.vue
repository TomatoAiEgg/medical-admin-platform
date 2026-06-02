<script setup lang="ts">
import type { KnowledgeChunk } from '@/api/medical';
import {
  checkKnowledgeQuality,
  listKnowledgeChunks,
  listMetadataRevisions,
  listQualityChecks,
  listRetrievalLogs,
  reviseKnowledgeMetadata,
  testKnowledgeRetrieval,
} from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import { pickData, pickRows } from '../_utils/format';

const loading = ref(false);
const actionLoading = ref(false);
const chunks = ref<KnowledgeChunk[]>([]);
const qualityChecks = ref<Record<string, any>[]>([]);
const retrievalLogs = ref<Record<string, any>[]>([]);
const metadataRevisions = ref<Record<string, any>[]>([]);
const retrievalHits = ref<Record<string, any>[]>([]);
const selectedHit = ref<Record<string, any> | null>(null);
const hitDrawerVisible = ref(false);

const namespaces = [
  { label: '导诊知识', value: 'default-guide-knowledge' },
  { label: '分诊知识', value: 'default-triage-knowledge' },
  { label: '挂号规则', value: 'default-registration-policy' },
  { label: '服务政策', value: 'hospital-service-policy' },
  { label: '科室医生资料', value: 'department-doctor-profile' },
  { label: 'FAQ', value: 'faq-common' },
];

const retrievalForm = reactive({
  namespace: 'default-guide-knowledge',
  query: '',
  embeddingModel: 'text-embedding-v2',
  topK: 5,
  minScore: 0,
});

const metadataForm = reactive({
  targetType: 'CHUNK' as 'DOCUMENT' | 'CHUNK',
  targetId: '',
  metadataText: '{}',
  changeReason: '',
});

const overview = computed(() => {
  const openIssues = qualityChecks.value.filter(item => item.status === 'OPEN').length;
  const successLogs = retrievalLogs.value.filter(item => item.status === 'HIT').length;
  const avgLatency = average(retrievalLogs.value.map(item => Number(item.latency_ms || 0)).filter(Boolean));
  return [
    { title: '可检索片段', value: chunks.value.length, desc: '当前 namespace', tone: 'primary' as const, icon: 'Collection' },
    { title: '本次命中', value: retrievalHits.value.length, desc: '检索测试返回', tone: 'success' as const, icon: 'Search' },
    { title: '开放质量问题', value: openIssues, desc: '待治理 chunk', tone: 'danger' as const, icon: 'WarningFilled' },
    { title: '平均耗时', value: `${avgLatency}ms`, desc: '按日志粗略估算', tone: 'info' as const, icon: 'DataAnalysis' },
    { title: '成功日志', value: successLogs, desc: '检索成功次数', tone: 'success' as const, icon: 'CircleCheckFilled' },
  ];
});

function average(values: number[]) {
  if (!values.length)
    return 0;
  return Math.round(values.reduce((sum, item) => sum + item, 0) / values.length);
}

function statusType(status?: string) {
  if (['SUCCESS', 'ACTIVE', 'RESOLVED', 'HIT'].includes(status || ''))
    return 'success';
  if (['RUNNING', 'PENDING', 'MISS'].includes(status || ''))
    return 'warning';
  if (['FAILED', 'OPEN', 'RETRIEVAL_ERROR'].includes(status || ''))
    return 'danger';
  return 'info';
}

function severityType(severity?: string) {
  if (['HIGH', 'CRITICAL', 'ERROR'].includes(severity || ''))
    return 'danger';
  if (['MEDIUM', 'WARN'].includes(severity || ''))
    return 'warning';
  return 'info';
}

function parseJsonObject(text: string) {
  if (!text.trim())
    return {};
  const value = JSON.parse(text);
  if (!value || Array.isArray(value) || typeof value !== 'object')
    throw new Error('metadata 必须是 JSON 对象');
  return value as Record<string, unknown>;
}

async function loadRows() {
  loading.value = true;
  try {
    const params = {
      pageNum: 1,
      pageSize: 80,
      namespace: retrievalForm.namespace,
    };
    const [chunkRes, qualityRes, retrievalRes, metadataRes] = await Promise.all([
      listKnowledgeChunks(params),
      listQualityChecks(params),
      listRetrievalLogs(params),
      listMetadataRevisions({ pageNum: 1, pageSize: 80 }),
    ]);
    chunks.value = pickRows<KnowledgeChunk>(chunkRes);
    qualityChecks.value = pickRows<Record<string, any>>(qualityRes);
    retrievalLogs.value = pickRows<Record<string, any>>(retrievalRes);
    metadataRevisions.value = pickRows<Record<string, any>>(metadataRes);
  }
  finally {
    loading.value = false;
  }
}

async function submitRetrievalTest() {
  if (!retrievalForm.namespace || !retrievalForm.query) {
    ElMessage.error('namespace 和检索问题不能为空');
    return;
  }
  actionLoading.value = true;
  try {
    const res = await testKnowledgeRetrieval({
      namespace: retrievalForm.namespace,
      query: retrievalForm.query,
      embeddingModel: retrievalForm.embeddingModel,
      topK: retrievalForm.topK,
      minScore: retrievalForm.minScore,
    });
    const data = pickData<{ hits?: Record<string, any>[] }>(res, {});
    retrievalHits.value = data.hits || [];
    await loadRows();
  }
  finally {
    actionLoading.value = false;
  }
}

async function runQualityCheck() {
  actionLoading.value = true;
  try {
    await checkKnowledgeQuality();
    ElMessage.success('质量检查已完成');
    await loadRows();
  }
  finally {
    actionLoading.value = false;
  }
}

function selectChunk(row: KnowledgeChunk) {
  metadataForm.targetType = 'CHUNK';
  metadataForm.targetId = row.id;
  metadataForm.metadataText = typeof row.metadata === 'string' ? row.metadata : JSON.stringify(row.metadata || {}, null, 2);
  metadataForm.changeReason = '知识治理台手动修订';
}

function openHit(row: Record<string, any>) {
  selectedHit.value = row;
  hitDrawerVisible.value = true;
  metadataForm.targetType = 'CHUNK';
  metadataForm.targetId = String(row.id || row.chunkId || '');
  metadataForm.metadataText = JSON.stringify(row.metadata || {}, null, 2);
}

async function submitMetadataRevision() {
  if (!metadataForm.targetId) {
    ElMessage.error('目标ID不能为空');
    return;
  }
  actionLoading.value = true;
  try {
    await reviseKnowledgeMetadata({
      targetType: metadataForm.targetType,
      targetId: metadataForm.targetId,
      metadata: parseJsonObject(metadataForm.metadataText),
      changeReason: metadataForm.changeReason,
    });
    ElMessage.success('metadata 已保存');
    await loadRows();
  }
  catch (error: any) {
    ElMessage.error(error?.message || 'metadata 格式不正确');
  }
  finally {
    actionLoading.value = false;
  }
}

function markFeedback(type: '满意' | '不满意' | '加入评估集') {
  ElMessage.success(`已标记：${type}`);
}

onMounted(loadRows);
</script>

<template>
  <div class="medical-page knowledge-governance-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">知识库治理</h1>
        <p class="medical-page__desc">
          面向 AI 运营人员的 RAG 检索测试台，查看召回片段、质量问题、metadata 修订和检索日志。
        </p>
      </div>
      <div class="medical-actions">
        <el-button :loading="actionLoading" @click="runQualityCheck">
          质量检查
        </el-button>
        <el-button type="primary" :loading="loading" @click="loadRows">
          刷新
        </el-button>
      </div>
    </div>

    <div class="medical-grid governance-metrics">
      <MetricCard v-for="item in overview" :key="item.title" v-bind="item" />
    </div>

    <div class="governance-layout">
      <section class="medical-section retrieval-panel">
        <div class="medical-section__header">
          <h2 class="medical-section__title">检索测试台</h2>
          <span class="medical-muted">不直接生成回答，只评估召回质量</span>
        </div>
        <div class="medical-section__body">
          <el-form label-width="96px">
            <el-form-item label="知识空间">
              <el-select v-model="retrievalForm.namespace" filterable @change="loadRows">
                <el-option v-for="item in namespaces" :key="item.value" :label="`${item.label} / ${item.value}`" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="问题">
              <el-input v-model="retrievalForm.query" type="textarea" :rows="5" placeholder="请输入要测试的用户问题，例如：儿童发热应该挂哪个科？" />
            </el-form-item>
            <div class="retrieval-controls">
              <el-form-item label="模型">
                <el-input v-model="retrievalForm.embeddingModel" />
              </el-form-item>
              <el-form-item label="topK">
                <el-input-number v-model="retrievalForm.topK" :min="1" :max="50" />
              </el-form-item>
              <el-form-item label="最低分">
                <el-input-number v-model="retrievalForm.minScore" :min="0" :max="1" :step="0.05" />
              </el-form-item>
            </div>
            <el-form-item>
              <el-button type="primary" :loading="actionLoading" @click="submitRetrievalTest">
                开始检索
              </el-button>
              <el-button @click="retrievalForm.query = ''">
                清空问题
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </section>

      <section class="medical-section hits-panel">
        <div class="medical-section__header">
          <h2 class="medical-section__title">召回结果</h2>
          <div class="hit-actions">
            <el-button size="small" @click="markFeedback('满意')">满意</el-button>
            <el-button size="small" @click="markFeedback('不满意')">不满意</el-button>
            <el-button size="small" type="primary" @click="markFeedback('加入评估集')">加入评估集</el-button>
          </div>
        </div>
        <el-table :data="retrievalHits" class="medical-table" height="360" empty-text="暂无召回结果" @row-click="openHit">
          <el-table-column prop="id" label="片段ID" min-width="190" show-overflow-tooltip />
          <el-table-column prop="score" label="分数" width="100" />
          <el-table-column prop="sourceName" label="来源" min-width="160" show-overflow-tooltip />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="content" label="内容" min-width="360" show-overflow-tooltip />
        </el-table>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">可检索片段</h2>
        </div>
        <el-table v-loading="loading" :data="chunks" class="medical-table" height="320" empty-text="暂无片段" @row-click="selectChunk">
          <el-table-column prop="id" label="片段ID" min-width="190" show-overflow-tooltip />
          <el-table-column prop="chunk_index" label="序号" width="80" />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="content" label="内容" min-width="360" show-overflow-tooltip />
          <el-table-column prop="embedding_model" label="模型" min-width="150" />
        </el-table>
      </section>

      <section class="medical-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">质量问题</h2>
        </div>
        <el-table v-loading="loading" :data="qualityChecks" class="medical-table" height="320" empty-text="暂无质量问题">
          <el-table-column prop="chunk_id" label="片段ID" min-width="190" show-overflow-tooltip />
          <el-table-column prop="issue_type" label="问题类型" min-width="160" />
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="severityType(row.severity)">{{ row.severity || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ row.status || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="issue_detail" label="详情" min-width="260" show-overflow-tooltip />
        </el-table>
      </section>

      <section class="medical-section metadata-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">metadata 修订</h2>
        </div>
        <div class="medical-section__body">
          <el-form label-width="90px">
            <el-form-item label="目标类型">
              <el-select v-model="metadataForm.targetType">
                <el-option label="文档" value="DOCUMENT" />
                <el-option label="片段" value="CHUNK" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标ID">
              <el-input v-model="metadataForm.targetId" />
            </el-form-item>
            <el-form-item label="原因">
              <el-input v-model="metadataForm.changeReason" />
            </el-form-item>
            <el-form-item label="metadata">
              <el-input v-model="metadataForm.metadataText" type="textarea" :rows="7" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="actionLoading" @click="submitMetadataRevision">
                保存修订
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </section>

      <section class="medical-section logs-section">
        <div class="medical-section__header">
          <h2 class="medical-section__title">检索日志</h2>
        </div>
        <el-table v-loading="loading" :data="retrievalLogs" class="medical-table" height="320" empty-text="暂无检索日志">
          <el-table-column prop="namespace" label="namespace" min-width="170" />
          <el-table-column prop="query_text" label="问题" min-width="260" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ row.status || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="hit_count" label="命中" width="90" />
          <el-table-column prop="best_score" label="最高分" width="110" />
          <el-table-column prop="latency_ms" label="耗时ms" width="110" />
        </el-table>
      </section>
    </div>

    <el-drawer v-model="hitDrawerVisible" title="召回片段详情" size="620px">
      <div class="medical-drawer-body">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="片段ID">{{ selectedHit?.id || selectedHit?.chunkId }}</el-descriptions-item>
          <el-descriptions-item label="分数">{{ selectedHit?.score }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ selectedHit?.sourceName }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ selectedHit?.title }}</el-descriptions-item>
        </el-descriptions>
        <div>
          <h3>内容</h3>
          <div class="hit-content">{{ selectedHit?.content }}</div>
        </div>
        <div>
          <h3>metadata</h3>
          <pre class="medical-json">{{ JSON.stringify(selectedHit?.metadata || {}, null, 2) }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.governance-metrics {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-bottom: 12px;
}

.governance-layout {
  display: grid;
  grid-template-columns: 0.85fr 1.15fr;
  gap: 12px;
}

.metadata-section,
.logs-section {
  grid-column: span 1;
}

.retrieval-controls {
  display: grid;
  grid-template-columns: 1fr 160px 160px;
  gap: 10px;
}

.hit-actions {
  display: flex;
  gap: 6px;
  align-items: center;
}

.hit-content {
  padding: 12px;
  line-height: 22px;
  white-space: pre-wrap;
  background: var(--medical-surface-soft);
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius);
}

h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

@media (max-width: 1300px) {
  .governance-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .governance-layout {
    grid-template-columns: 1fr;
  }
}
</style>
