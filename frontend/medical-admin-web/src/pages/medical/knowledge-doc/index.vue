<script setup lang="ts">
import type { KnowledgeChunk, KnowledgeDocument, KnowledgeNamespaceSummary } from '@/api/medical';
import {
  checkKnowledgeQuality,
  importKnowledgeDocument,
  listCleanTasks,
  listKnowledgeChunks,
  listKnowledgeDocuments,
  listKnowledgeNamespaceSummary,
  listQualityChecks,
  listRetrievalLogs,
  listVectorTasks,
  rebuildKnowledgeVector,
  runKnowledgeClean,
} from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import { displayValue, pickData, pickRows } from '../_utils/format';

const loading = ref(false);
const actionLoading = ref(false);
const importVisible = ref(false);
const detailVisible = ref(false);
const documents = ref<KnowledgeDocument[]>([]);
const chunks = ref<KnowledgeChunk[]>([]);
const cleanTasks = ref<Record<string, any>[]>([]);
const vectorTasks = ref<Record<string, any>[]>([]);
const qualityChecks = ref<Record<string, any>[]>([]);
const retrievalLogs = ref<Record<string, any>[]>([]);
const selectedDocument = ref<KnowledgeDocument | null>(null);
const namespaceSummary = ref<KnowledgeNamespaceSummary[]>([]);

const namespaces = [
  { label: '导诊知识', value: 'default-guide-knowledge' },
  { label: '分诊知识', value: 'default-triage-knowledge' },
  { label: '挂号规则', value: 'default-registration-policy' },
  { label: '服务政策', value: 'hospital-service-policy' },
  { label: '科室医生资料', value: 'department-doctor-profile' },
  { label: 'FAQ', value: 'faq-common' },
];

const filters = reactive({
  namespace: 'default-guide-knowledge',
  documentId: '',
  title: '',
  status: '',
});

const importForm = reactive({
  namespace: 'default-guide-knowledge',
  sourceId: '',
  sourceName: '',
  documentType: 'GUIDE',
  title: '',
  version: 'v1',
  status: 'ACTIVE',
  embeddingModel: 'text-embedding-v2',
  chunkSize: 800,
  chunkOverlap: 80,
  metadataText: '{}',
  content: '',
});

const overview = computed(() => {
  const activeDocs = documents.value.filter(item => item.status === 'ACTIVE').length;
  const vectorFailed = vectorTasks.value.filter(item => item.status === 'FAILED' || Number(item.fail_count || 0) > 0).length;
  const openIssues = qualityChecks.value.filter(item => item.status === 'OPEN').length;
  return [
    { title: '文档总数', value: documents.value.length, desc: '当前 namespace', tone: 'primary' as const, icon: 'Document' },
    { title: '已发布', value: activeDocs, desc: '可被业务检索', tone: 'success' as const, icon: 'CircleCheckFilled' },
    { title: '切片数量', value: chunks.value.length, desc: '当前筛选范围', tone: 'warning' as const, icon: 'Tickets' },
    { title: '待处理问题', value: openIssues + vectorFailed, desc: '质量或向量任务异常', tone: 'danger' as const, icon: 'WarningFilled' },
  ];
});

const namespaceCounts = computed(() => {
  const map = new Map<string, KnowledgeNamespaceSummary>();
  namespaceSummary.value.forEach((item) => {
    if (item.namespace)
      map.set(item.namespace, item);
  });
  documents.value.forEach((item) => {
    if (!item.namespace)
      return;
    const current = map.get(item.namespace);
    if (!current) {
      map.set(item.namespace, {
        namespace: item.namespace,
        document_count: 1,
        active_count: item.status === 'ACTIVE' ? 1 : 0,
      });
      return;
    }
    const documentCount = Number(current.document_count || 0);
    if (item.namespace === filters.namespace && documentCount < documents.value.length) {
      current.document_count = documents.value.length;
      current.active_count = documents.value.filter(doc => doc.status === 'ACTIVE').length;
    }
  });
  return map;
});

function namespaceDocumentCount(namespace: string) {
  return Number(namespaceCounts.value.get(namespace)?.document_count || 0);
}

function namespaceActiveCount(namespace: string) {
  return Number(namespaceCounts.value.get(namespace)?.active_count || 0);
}

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    ACTIVE: '已发布',
    DRAFT: '草稿',
    ARCHIVED: '已归档',
    PENDING: '待处理',
    RUNNING: '运行中',
    SUCCESS: '成功',
    HIT: '命中',
    MISS: '未命中',
    RETRIEVAL_ERROR: '检索失败',
    COMPLETED: '完成',
    FAILED: '失败',
    OPEN: '待处理',
    RESOLVED: '已解决',
    IGNORED: '已忽略',
  };
  return status ? `${map[status] || status} / ${status}` : '-';
}

function statusType(status?: string) {
  if (['ACTIVE', 'SUCCESS', 'COMPLETED', 'RESOLVED', 'HIT'].includes(status || ''))
    return 'success';
  if (['PENDING', 'RUNNING', 'DRAFT', 'MISS'].includes(status || ''))
    return 'warning';
  if (['FAILED', 'OPEN', 'RETRIEVAL_ERROR'].includes(status || ''))
    return 'danger';
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
      namespace: filters.namespace,
      documentId: filters.documentId,
      title: filters.title,
      status: filters.status,
    };
    const [documentRes, chunkRes, cleanRes, vectorRes, qualityRes, retrievalRes] = await Promise.all([
      listKnowledgeDocuments(params),
      listKnowledgeChunks(params),
      listCleanTasks(params),
      listVectorTasks(params),
      listQualityChecks(params),
      listRetrievalLogs(params),
    ]);
    documents.value = pickRows<KnowledgeDocument>(documentRes);
    chunks.value = pickRows<KnowledgeChunk>(chunkRes);
    cleanTasks.value = pickRows<Record<string, any>>(cleanRes);
    vectorTasks.value = pickRows<Record<string, any>>(vectorRes);
    qualityChecks.value = pickRows<Record<string, any>>(qualityRes);
    retrievalLogs.value = pickRows<Record<string, any>>(retrievalRes);
  }
  finally {
    loading.value = false;
  }
}

async function loadNamespaceSummary() {
  const res = await listKnowledgeNamespaceSummary();
  namespaceSummary.value = pickRows<KnowledgeNamespaceSummary>(res);
}

async function refreshPage() {
  loading.value = true;
  try {
    await Promise.all([loadRows(), loadNamespaceSummary()]);
  }
  finally {
    loading.value = false;
  }
}

function selectNamespace(namespace: string) {
  filters.namespace = namespace;
  filters.documentId = '';
  selectedDocument.value = null;
  loadRows();
}

async function openDetail(row: KnowledgeDocument) {
  selectedDocument.value = row;
  filters.namespace = row.namespace;
  filters.documentId = row.id;
  detailVisible.value = true;
  await loadRows();
}

async function cleanDocument(row: KnowledgeDocument) {
  actionLoading.value = true;
  try {
    await runKnowledgeClean({ documentId: row.id, cleanStrategy: 'NORMALIZE_TEXT' });
    ElMessage.success('清洗任务已提交');
    await loadRows();
  }
  finally {
    actionLoading.value = false;
  }
}

async function rebuildDocumentVector(row: KnowledgeDocument) {
  actionLoading.value = true;
  try {
    await rebuildKnowledgeVector({ documentId: row.id, embeddingModel: importForm.embeddingModel });
    ElMessage.success('向量化任务已提交');
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

function beforeUploadDocument(file: any) {
  const reader = new FileReader();
  reader.onload = () => {
    importForm.content = String(reader.result || '');
    if (!importForm.title)
      importForm.title = file.name?.replace(/\.[^.]+$/, '') || '';
    if (!importForm.sourceId)
      importForm.sourceId = `${Date.now()}-${file.name || 'document'}`;
    if (!importForm.sourceName)
      importForm.sourceName = file.name || importForm.sourceId;
  };
  reader.readAsText(file);
  return false;
}

function handleUploadDocumentChange(file: any) {
  const raw = file?.raw || file;
  if (raw)
    beforeUploadDocument(raw);
}

async function submitImport() {
  if (!importForm.namespace || !importForm.sourceId || !importForm.title || !importForm.content) {
    ElMessage.error('namespace、sourceId、标题和正文不能为空');
    return;
  }
  actionLoading.value = true;
  try {
    const metadata = parseJsonObject(importForm.metadataText);
    const res = await importKnowledgeDocument({
      namespace: importForm.namespace,
      sourceId: importForm.sourceId,
      sourceName: importForm.sourceName,
      documentType: importForm.documentType,
      title: importForm.title,
      version: importForm.version,
      status: importForm.status,
      embeddingModel: importForm.embeddingModel,
      chunkSize: importForm.chunkSize,
      chunkOverlap: importForm.chunkOverlap,
      content: importForm.content,
      metadata,
    });
    const data = pickData<Record<string, unknown>>(res, {});
    filters.namespace = importForm.namespace;
    filters.documentId = String(data.documentId || '');
    importVisible.value = false;
    ElMessage.success('知识文档已导入');
    await refreshPage();
  }
  catch (error: any) {
    ElMessage.error(error?.message || '导入失败');
  }
  finally {
    actionLoading.value = false;
  }
}

onMounted(refreshPage);
</script>

<template>
  <div class="medical-page knowledge-doc-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">知识库文档管理</h1>
        <p class="medical-page__desc">
          管理医疗知识文档、命名空间、切片、清洗和向量化任务，支撑导诊、分诊和挂号规则检索。
        </p>
      </div>
      <div class="medical-actions">
        <el-button @click="importVisible = true">
          上传文档
        </el-button>
        <el-button :loading="actionLoading" @click="runQualityCheck">
          质量检查
        </el-button>
        <el-button type="primary" :loading="loading" @click="refreshPage">
          刷新
        </el-button>
      </div>
    </div>

    <div class="medical-grid medical-grid--4 overview-grid">
      <MetricCard v-for="item in overview" :key="item.title" v-bind="item" />
    </div>

    <div class="knowledge-doc-layout">
      <aside class="namespace-panel">
        <div class="namespace-panel__header">
          <strong>知识空间</strong>
          <span>Namespace</span>
        </div>
        <button
          v-for="item in namespaces"
          :key="item.value"
          type="button"
          class="namespace-item"
          :class="{ active: filters.namespace === item.value }"
          @click="selectNamespace(item.value)"
        >
          <span>{{ item.label }}</span>
          <small>{{ namespaceActiveCount(item.value) }}/{{ namespaceDocumentCount(item.value) }}</small>
        </button>
      </aside>

      <main class="knowledge-doc-main">
        <section class="medical-filter">
          <el-form :model="filters" inline>
            <el-form-item label="namespace">
              <el-input v-model="filters.namespace" clearable placeholder="namespace" />
            </el-form-item>
            <el-form-item label="文档ID">
              <el-input v-model="filters.documentId" clearable placeholder="documentId" />
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="filters.title" clearable placeholder="文档标题" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
                <el-option label="已发布 / ACTIVE" value="ACTIVE" />
                <el-option label="草稿 / DRAFT" value="DRAFT" />
                <el-option label="已归档 / ARCHIVED" value="ARCHIVED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadRows">
                查询
              </el-button>
            </el-form-item>
          </el-form>
        </section>

        <section class="medical-section">
          <div class="medical-section__header">
            <h2 class="medical-section__title">文档列表</h2>
            <span class="medical-muted">共 {{ documents.length }} 条记录</span>
          </div>
          <el-table v-loading="loading" :data="documents" class="medical-table" height="calc(100vh - 465px)" empty-text="暂无知识文档" @row-click="openDetail">
            <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="namespace" label="namespace" min-width="190" show-overflow-tooltip />
            <el-table-column prop="document_type" label="类型" width="130" />
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" effect="light" round>
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column prop="created_at" label="创建时间" min-width="170" show-overflow-tooltip />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <div class="medical-table-actions">
                  <el-button link type="primary" @click.stop="openDetail(row)">
                    详情
                  </el-button>
                  <el-dropdown trigger="click" :disabled="actionLoading" @click.stop>
                    <el-button link type="primary" :loading="actionLoading">
                      更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item @click.stop="cleanDocument(row)">
                          清洗
                        </el-dropdown-item>
                        <el-dropdown-item @click.stop="rebuildDocumentVector(row)">
                          向量化
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </main>
    </div>

    <el-drawer v-model="detailVisible" title="文档详情" size="78%">
      <div class="medical-drawer-body">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文档标题">{{ selectedDocument?.title }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ displayValue(selectedDocument?.document_type) }}</el-descriptions-item>
          <el-descriptions-item label="namespace">{{ selectedDocument?.namespace }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(selectedDocument?.status) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ displayValue(selectedDocument?.source_name || selectedDocument?.source_id) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ displayValue(selectedDocument?.version) }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs>
          <el-tab-pane label="切片">
            <el-table :data="chunks" class="medical-table" empty-text="暂无切片">
              <el-table-column prop="chunk_index" label="序号" width="80" />
              <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
              <el-table-column prop="content" label="内容" min-width="420" show-overflow-tooltip />
              <el-table-column prop="embedding_model" label="模型" min-width="160" />
              <el-table-column prop="embedding_dimensions" label="维度" width="90" />
              <el-table-column label="启用" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="清洗任务">
            <el-table :data="cleanTasks" class="medical-table" empty-text="暂无清洗任务">
              <el-table-column prop="task_id" label="任务ID" min-width="220" show-overflow-tooltip />
              <el-table-column label="状态" width="130">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="clean_strategy" label="策略" min-width="150" />
              <el-table-column prop="before_length" label="清洗前" width="100" />
              <el-table-column prop="after_length" label="清洗后" width="100" />
              <el-table-column prop="created_at" label="创建时间" min-width="170" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="向量任务">
            <el-table :data="vectorTasks" class="medical-table" empty-text="暂无向量任务">
              <el-table-column prop="task_id" label="任务ID" min-width="220" show-overflow-tooltip />
              <el-table-column prop="task_type" label="类型" width="110" />
              <el-table-column label="状态" width="130">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="embedding_model" label="模型" min-width="160" />
              <el-table-column prop="chunk_count" label="切片" width="90" />
              <el-table-column prop="success_count" label="成功" width="90" />
              <el-table-column prop="fail_count" label="失败" width="90" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="质量问题">
            <el-table :data="qualityChecks" class="medical-table" empty-text="暂无质量问题">
              <el-table-column prop="issue_type" label="问题类型" min-width="160" />
              <el-table-column prop="severity" label="级别" width="100" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="issue_detail" label="问题详情" min-width="320" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="检索日志">
            <el-table :data="retrievalLogs" class="medical-table" empty-text="暂无检索日志">
              <el-table-column prop="query_text" label="问题" min-width="260" show-overflow-tooltip />
              <el-table-column prop="hit_count" label="命中" width="90" />
              <el-table-column prop="best_score" label="最高分" width="110" />
              <el-table-column prop="latency_ms" label="耗时ms" width="110" />
              <el-table-column prop="created_at" label="时间" min-width="170" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="importVisible" title="上传/导入知识文档" width="760px">
      <el-upload drag action="#" :auto-upload="false" :show-file-list="false" :on-change="handleUploadDocumentChange" :before-upload="beforeUploadDocument">
        <div class="upload-text">拖入或选择 .txt / .md / .json 文档</div>
      </el-upload>
      <el-form class="upload-form" label-width="110px">
        <el-form-item label="namespace">
          <el-input v-model="importForm.namespace" />
        </el-form-item>
        <el-form-item label="sourceId">
          <el-input v-model="importForm.sourceId" />
        </el-form-item>
        <el-form-item label="sourceName">
          <el-input v-model="importForm.sourceName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="importForm.documentType">
            <el-option label="导诊" value="GUIDE" />
            <el-option label="分诊" value="TRIAGE" />
            <el-option label="挂号策略" value="REGISTRATION_POLICY" />
            <el-option label="FAQ" value="FAQ" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="importForm.title" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="importForm.content" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="metadata">
          <el-input v-model="importForm.metadataText" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitImport">
          导入并向量化
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.overview-grid {
  margin-bottom: 12px;
}

.knowledge-doc-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 12px;
}

.namespace-panel {
  min-height: calc(100vh - 330px);
  padding: 10px;
  background: var(--medical-surface);
  border: 1px solid var(--medical-border);
  border-radius: var(--medical-radius-lg);
}

.namespace-panel__header {
  padding: 8px 8px 12px;
  border-bottom: 1px solid var(--medical-border);
}

.namespace-panel__header strong,
.namespace-panel__header span {
  display: block;
}

.namespace-panel__header span {
  margin-top: 2px;
  font-size: 12px;
  color: var(--medical-text-muted);
}

.namespace-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 36px;
  padding: 0 10px;
  margin-top: 6px;
  color: var(--medical-text-secondary);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--medical-radius);
}

.namespace-item:hover,
.namespace-item.active {
  color: var(--medical-primary);
  background: var(--medical-primary-soft);
}

.namespace-item small {
  padding: 1px 6px;
  color: var(--medical-text-muted);
  background: var(--medical-surface-soft);
  border-radius: 999px;
}

.upload-form {
  margin-top: 16px;
}

.upload-text {
  color: var(--medical-text-secondary);
}

@media (max-width: 1100px) {
  .knowledge-doc-layout {
    grid-template-columns: 1fr;
  }
}
</style>
