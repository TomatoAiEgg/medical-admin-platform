<script setup lang="ts">
import type { PatientProfile } from '@/api/medical';
import { addPatient, listPatients, updatePatient } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import { displayValue, pickRows } from '../_utils/format';
import { sourceLabel, sourceOptions } from '../_utils/lookup';

const loading = ref(false);
const saving = ref(false);
const rows = ref<PatientProfile[]>([]);
const total = ref(0);
const editVisible = ref(false);
const isCreate = ref(false);

const query = reactive({
  patientName: '',
  phoneMasked: '',
  verifiedStatus: '',
  pageNum: 1,
  pageSize: 20,
});

const form = reactive<PatientProfile>({
  patient_id: '',
  patient_name: '',
  id_type: 'ID_CARD',
  id_number_masked: '',
  phone_masked: '',
  active: true,
  verified_status: 'VERIFIED',
  source_channel: 'ADMIN',
});

const verifiedOptions = [
  { label: '已认证', value: 'VERIFIED' },
  { label: '未认证', value: 'UNVERIFIED' },
  { label: '异常', value: 'ABNORMAL' },
];

const metrics = computed(() => [
  { title: '患者总数', value: total.value || rows.value.length, desc: '当前筛选结果', tone: 'primary' as const, icon: 'User' },
  { title: '已认证', value: rows.value.filter(item => item.verified_status === 'VERIFIED').length, desc: '实名或证件通过', tone: 'success' as const, icon: 'CircleCheck' },
  { title: '异常资料', value: rows.value.filter(item => item.verified_status === 'ABNORMAL').length, desc: '需要运营复核', tone: 'danger' as const, icon: 'WarningFilled' },
  { title: '停用患者', value: rows.value.filter(item => item.active === false).length, desc: '不可参与挂号', tone: 'info' as const, icon: 'RemoveFilled' },
]);

function pickTotal(res: unknown, fallback: number) {
  const data = res as { total?: number; data?: { total?: number } };
  return data?.total ?? data?.data?.total ?? fallback;
}

function verifiedLabel(value?: string) {
  return verifiedOptions.find(item => item.value === value)?.label || displayValue(value);
}

function verifiedType(value?: string) {
  if (value === 'VERIFIED')
    return 'success';
  if (value === 'ABNORMAL')
    return 'danger';
  return 'warning';
}

function makePatientId() {
  return `PAT-${Date.now()}`;
}

async function loadRows() {
  loading.value = true;
  try {
    const res = await listPatients({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      patientName: query.patientName,
      phoneMasked: query.phoneMasked,
      verifiedStatus: query.verifiedStatus,
    });
    rows.value = pickRows<PatientProfile>(res);
    total.value = pickTotal(res, rows.value.length);
  }
  finally {
    loading.value = false;
  }
}

function resetForm(row?: PatientProfile) {
  form.patient_id = row?.patient_id || '';
  form.patient_name = row?.patient_name || '';
  form.id_type = row?.id_type || 'ID_CARD';
  form.id_number_masked = row?.id_number_masked || '';
  form.phone_masked = row?.phone_masked || '';
  form.active = row?.active ?? true;
  form.verified_status = row?.verified_status || 'VERIFIED';
  form.source_channel = row?.source_channel || 'ADMIN';
}

function openEditor(row?: PatientProfile) {
  isCreate.value = !row;
  resetForm(row);
  editVisible.value = true;
}

async function saveRow() {
  if (!form.patient_name) {
    ElMessage.warning('请填写患者姓名');
    return;
  }

  saving.value = true;
  try {
    if (isCreate.value) {
      form.patient_id = form.patient_id || makePatientId();
      await addPatient({ ...form });
    }
    else {
      await updatePatient(form.patient_id, { ...form });
    }
    ElMessage.success('患者信息已保存');
    editVisible.value = false;
    await loadRows();
  }
  finally {
    saving.value = false;
  }
}

function resetFilters() {
  query.patientName = '';
  query.phoneMasked = '';
  query.verifiedStatus = '';
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

onMounted(loadRows);
</script>

<template>
  <div class="medical-page">
    <div class="medical-page__header">
      <div>
        <h1 class="medical-page__title">患者管理</h1>
        <p class="medical-page__desc">维护挂号、监控和异常追踪使用的患者基础资料，敏感字段保持脱敏展示。</p>
      </div>
      <div class="medical-actions">
        <el-button type="primary" @click="openEditor()">新增患者</el-button>
        <el-button :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <el-form class="medical-filter" :model="query" inline>
      <el-form-item label="患者姓名">
        <el-input v-model="query.patientName" clearable placeholder="输入患者姓名" @keyup.enter="loadRows" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="query.phoneMasked" clearable placeholder="支持脱敏手机号" @keyup.enter="loadRows" />
      </el-form-item>
      <el-form-item label="认证状态">
        <el-select v-model="query.verifiedStatus" clearable placeholder="全部状态">
          <el-option v-for="item in verifiedOptions" :key="item.value" :label="item.label" :value="item.value" />
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
        <h2 class="medical-section__title">患者列表</h2>
        <span class="medical-muted">共 {{ total || rows.length }} 条</span>
      </div>
      <div class="medical-section__body">
        <el-table v-loading="loading" :data="rows" class="medical-table" table-layout="fixed">
          <el-table-column prop="patient_name" label="姓名" width="120" />
          <el-table-column prop="phone_masked" label="手机号" width="140" />
          <el-table-column prop="id_number_masked" label="证件号" min-width="170" show-overflow-tooltip />
          <el-table-column label="认证" width="104">
            <template #default="{ row }">
              <el-tag :type="verifiedType(row.verified_status)" effect="light">
                {{ verifiedLabel(row.verified_status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag :type="row.active === false ? 'info' : 'success'" effect="light">
                {{ row.active === false ? '停用' : '启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="110">
            <template #default="{ row }">{{ sourceLabel(row.source_channel) }}</template>
          </el-table-column>
          <el-table-column prop="updated_at" label="更新时间" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="86">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="patient-pagination">
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

    <el-dialog v-model="editVisible" :title="isCreate ? '新增患者' : '编辑患者'" width="580px">
      <el-form label-width="100px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.patient_name" placeholder="输入患者姓名" />
        </el-form-item>
        <el-form-item label="证件类型">
          <el-input v-model="form.id_type" placeholder="ID_CARD" />
        </el-form-item>
        <el-form-item label="证件号">
          <el-input v-model="form.id_number_masked" placeholder="仅保存或展示脱敏证件号" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone_masked" placeholder="仅保存或展示脱敏手机号" />
        </el-form-item>
        <el-form-item label="认证状态">
          <el-select v-model="form.verified_status">
            <el-option v-for="item in verifiedOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.active" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source_channel" filterable>
            <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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

.patient-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
