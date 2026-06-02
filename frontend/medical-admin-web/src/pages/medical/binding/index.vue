<script setup lang="ts">
import type { PatientProfile, PlatformUser, UserPatientBinding } from '@/api/medical';
import { addBinding, listBindings, updateBinding } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import { pickRows } from '../_utils/format';
import { loadPatients, loadPlatformUsers, patientName, patientOptionLabel, platformUserName, relationLabel, relationOptions, userOptionLabel } from '../_utils/lookup';

const loading = ref(false);
const saving = ref(false);
const rows = ref<UserPatientBinding[]>([]);
const users = ref<PlatformUser[]>([]);
const patients = ref<PatientProfile[]>([]);
const total = ref(0);
const editVisible = ref(false);
const isCreate = ref(false);

const query = reactive({
  userId: '',
  patientId: '',
  pageNum: 1,
  pageSize: 20,
});

const form = reactive<UserPatientBinding>({
  user_id: '',
  patient_id: '',
  relation_code: 'SELF',
  is_default: false,
  active: true,
});

const metrics = computed(() => [
  { title: '绑定总数', value: total.value || rows.value.length, desc: '当前筛选结果', tone: 'primary' as const, icon: 'Connection' },
  { title: '默认就诊人', value: rows.value.filter(item => item.is_default).length, desc: '用户首选就诊人', tone: 'success' as const, icon: 'CircleCheck' },
  { title: '停用关系', value: rows.value.filter(item => item.active === false).length, desc: '不可下单使用', tone: 'warning' as const, icon: 'RemoveFilled' },
  { title: '关系类型', value: new Set(rows.value.map(item => item.relation_code).filter(Boolean)).size, desc: '本人/亲属等', tone: 'info' as const, icon: 'CollectionTag' },
]);

function pickTotal(res: unknown, fallback: number) {
  const data = res as { total?: number; data?: { total?: number } };
  return data?.total ?? data?.data?.total ?? fallback;
}

async function loadRows() {
  loading.value = true;
  try {
    const res = await listBindings({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      userId: query.userId,
      patientId: query.patientId,
    });
    rows.value = pickRows<UserPatientBinding>(res);
    total.value = pickTotal(res, rows.value.length);
  }
  finally {
    loading.value = false;
  }
}

async function loadLookups() {
  const [userRows, patientRows] = await Promise.all([loadPlatformUsers(), loadPatients()]);
  users.value = userRows;
  patients.value = patientRows;
}

function resetForm(row?: UserPatientBinding) {
  form.binding_id = row?.binding_id;
  form.user_id = row?.user_id || '';
  form.patient_id = row?.patient_id || '';
  form.relation_code = row?.relation_code || 'SELF';
  form.is_default = row?.is_default ?? false;
  form.active = row?.active ?? true;
}

function openEditor(row?: UserPatientBinding) {
  isCreate.value = !row;
  resetForm(row);
  editVisible.value = true;
}

async function saveRow() {
  if (!form.user_id || !form.patient_id) {
    ElMessage.warning('请选择平台用户和就诊人');
    return;
  }

  saving.value = true;
  try {
    const payload = { ...form };
    if (isCreate.value)
      await addBinding(payload);
    else if (form.binding_id)
      await updateBinding(form.binding_id, payload);
    ElMessage.success('绑定关系已保存');
    editVisible.value = false;
    await loadRows();
  }
  finally {
    saving.value = false;
  }
}

function resetFilters() {
  query.userId = '';
  query.patientId = '';
  query.pageNum = 1;
  loadRows();
}

function handlePageChange(page: number) {
  query.pageNum = page;
  loadRows();
}

function handleSizeChange(size: number) {
  query.pageSize = size;
  query.pageNum = 1;
  loadRows();
}

onMounted(async () => {
  await Promise.all([loadRows(), loadLookups()]);
});
</script>

<template>
  <div class="medical-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">就诊人绑定</h1>
        <p class="medical-page__desc">维护平台用户与患者就诊人的绑定关系，用于代挂号、亲属管理和默认就诊人选择。</p>
      </div>
      <div class="medical-actions">
        <el-button type="primary" @click="openEditor()">新增绑定</el-button>
        <el-button :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <el-form class="medical-filter" :model="query" inline>
      <el-form-item label="平台用户">
        <el-select v-model="query.userId" clearable filterable placeholder="全部用户">
          <el-option v-for="item in users" :key="item.user_id" :label="userOptionLabel(item)" :value="item.user_id" />
        </el-select>
      </el-form-item>
      <el-form-item label="就诊人">
        <el-select v-model="query.patientId" clearable filterable placeholder="全部就诊人">
          <el-option v-for="item in patients" :key="item.patient_id" :label="patientOptionLabel(item)" :value="item.patient_id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="loadRows">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="medical-grid medical-grid--4">
      <MetricCard v-for="item in metrics" :key="item.title" v-bind="item" />
    </div>

    <section class="medical-section">
      <div class="medical-section__header">
        <h2 class="medical-section__title">绑定列表</h2>
        <span class="medical-muted">共 {{ total || rows.length }} 条</span>
      </div>
      <div class="medical-section__body">
        <el-table v-loading="loading" :data="rows" class="medical-table" table-layout="fixed">
          <el-table-column label="平台用户" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ platformUserName(row.user_id, users) }}</template>
          </el-table-column>
          <el-table-column label="就诊人" width="130">
            <template #default="{ row }">{{ patientName(row.patient_id, patients) }}</template>
          </el-table-column>
          <el-table-column label="关系" width="100">
            <template #default="{ row }">{{ relationLabel(row.relation_code) }}</template>
          </el-table-column>
          <el-table-column label="默认" width="86">
            <template #default="{ row }">
              <el-tag :type="row.is_default ? 'success' : 'info'" effect="light">
                {{ row.is_default ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag :type="row.active === false ? 'danger' : 'success'" effect="light">
                {{ row.active === false ? '停用' : '有效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bound_at" label="绑定时间" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="86">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="binding-pagination">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50, 100]"
            :total="total || rows.length"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </section>

    <el-dialog v-model="editVisible" :title="isCreate ? '新增绑定' : '编辑绑定'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="平台用户" required>
          <el-select v-model="form.user_id" :disabled="!isCreate" filterable placeholder="选择平台用户">
            <el-option v-for="item in users" :key="item.user_id" :label="userOptionLabel(item)" :value="item.user_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="就诊人" required>
          <el-select v-model="form.patient_id" :disabled="!isCreate" filterable placeholder="选择就诊人">
            <el-option v-for="item in patients" :key="item.patient_id" :label="patientOptionLabel(item)" :value="item.patient_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关系">
          <el-select v-model="form.relation_code">
            <el-option v-for="item in relationOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认就诊人">
          <el-switch v-model="form.is_default" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.active" active-text="有效" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRow">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.medical-page {
  display: grid;
  gap: 12px;
}

.binding-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
