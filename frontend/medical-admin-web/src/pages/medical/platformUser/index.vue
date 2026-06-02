<script setup lang="ts">
import type { PlatformUser } from '@/api/medical';
import { addPlatformUser, listPlatformUsers, updatePlatformUser } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import { displayValue, pickRows } from '../_utils/format';
import { sourceLabel, sourceOptions, userOptionLabel } from '../_utils/lookup';

const loading = ref(false);
const saving = ref(false);
const rows = ref<PlatformUser[]>([]);
const total = ref(0);
const editVisible = ref(false);
const isCreate = ref(false);

const query = reactive({
  displayName: '',
  status: '',
  pageNum: 1,
  pageSize: 20,
});

const form = reactive<PlatformUser>({
  user_id: '',
  open_id: '',
  union_id: '',
  nickname: '',
  display_name: '',
  status: 'ACTIVE',
  avatar_url: '',
  source_channel: 'ADMIN',
  phone_masked: '',
});

const statusOptions = [
  { label: '正常', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' },
  { label: '冻结', value: 'FROZEN' },
];

const metrics = computed(() => [
  { title: '用户总数', value: total.value || rows.value.length, desc: '当前筛选结果', tone: 'primary' as const, icon: 'Avatar' },
  { title: '正常用户', value: rows.value.filter(item => item.status === 'ACTIVE').length, desc: '可发起挂号', tone: 'success' as const, icon: 'CircleCheck' },
  { title: '受限用户', value: rows.value.filter(item => item.status && item.status !== 'ACTIVE').length, desc: '禁用或冻结', tone: 'warning' as const, icon: 'Lock' },
  { title: '来源渠道', value: new Set(rows.value.map(item => item.source_channel).filter(Boolean)).size, desc: '按渠道归集', tone: 'info' as const, icon: 'Share' },
]);

function pickTotal(res: unknown, fallback: number) {
  const data = res as { total?: number; data?: { total?: number } };
  return data?.total ?? data?.data?.total ?? fallback;
}

function statusLabel(value?: string) {
  return statusOptions.find(item => item.value === value)?.label || displayValue(value);
}

function statusType(value?: string) {
  if (value === 'ACTIVE')
    return 'success';
  if (value === 'FROZEN')
    return 'warning';
  return 'info';
}

function makeUserId() {
  return `USER-${Date.now()}`;
}

async function loadRows() {
  loading.value = true;
  try {
    const res = await listPlatformUsers({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      displayName: query.displayName,
      status: query.status,
    });
    rows.value = pickRows<PlatformUser>(res);
    total.value = pickTotal(res, rows.value.length);
  }
  finally {
    loading.value = false;
  }
}

function resetForm(row?: PlatformUser) {
  form.user_id = row?.user_id || '';
  form.open_id = row?.open_id || '';
  form.union_id = row?.union_id || '';
  form.nickname = row?.nickname || '';
  form.display_name = row?.display_name || '';
  form.status = row?.status || 'ACTIVE';
  form.avatar_url = row?.avatar_url || '';
  form.source_channel = row?.source_channel || 'ADMIN';
  form.phone_masked = row?.phone_masked || '';
}

function openEditor(row?: PlatformUser) {
  isCreate.value = !row;
  resetForm(row);
  editVisible.value = true;
}

async function saveRow() {
  if (!form.display_name && !form.nickname && !form.phone_masked) {
    ElMessage.warning('请填写真实姓名、昵称或手机号');
    return;
  }

  saving.value = true;
  try {
    if (isCreate.value) {
      form.user_id = form.user_id || makeUserId();
      await addPlatformUser({ ...form });
    }
    else {
      await updatePlatformUser(form.user_id, { ...form });
    }
    ElMessage.success('平台用户已保存');
    editVisible.value = false;
    await loadRows();
  }
  finally {
    saving.value = false;
  }
}

function resetFilters() {
  query.displayName = '';
  query.status = '';
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
        <h1 class="medical-page__title">平台用户</h1>
        <p class="medical-page__desc">维护前台登录用户资料，支撑挂号下单、就诊人绑定和风险监控。</p>
      </div>
      <div class="medical-actions">
        <el-button type="primary" @click="openEditor()">新增用户</el-button>
        <el-button :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <el-form class="medical-filter" :model="query" inline>
      <el-form-item label="用户">
        <el-input v-model="query.displayName" clearable placeholder="真实姓名或昵称" @keyup.enter="loadRows" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
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
        <h2 class="medical-section__title">用户列表</h2>
        <span class="medical-muted">共 {{ total || rows.length }} 条</span>
      </div>
      <div class="medical-section__body">
        <el-table v-loading="loading" :data="rows" class="medical-table" table-layout="fixed">
          <el-table-column label="平台用户" min-width="150">
            <template #default="{ row }">{{ userOptionLabel(row) }}</template>
          </el-table-column>
          <el-table-column prop="nickname" label="昵称" width="120" show-overflow-tooltip />
          <el-table-column prop="phone_masked" label="手机号" width="140" />
          <el-table-column label="来源" width="110">
            <template #default="{ row }">{{ sourceLabel(row.source_channel) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="104">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="light">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updated_at" label="更新时间" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="86">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="platform-pagination">
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

    <el-dialog v-model="editVisible" :title="isCreate ? '新增平台用户' : '编辑平台用户'" width="580px">
      <el-form label-width="100px">
        <el-form-item label="真实姓名">
          <el-input v-model="form.display_name" placeholder="前台登录用户真实姓名" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone_masked" placeholder="仅展示脱敏手机号" />
        </el-form-item>
        <el-form-item label="OpenID">
          <el-input v-model="form.open_id" placeholder="三方平台标识，可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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

.platform-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
