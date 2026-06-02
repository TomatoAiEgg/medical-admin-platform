<script setup lang="ts">
import type { Department } from '@/api/medical';
import { addDepartment, listDepartments, updateDepartment } from '@/api/medical';
import MetricCard from '../_components/MetricCard.vue';
import { displayValue, pickRows } from '../_utils/format';
import { categoryLabel, categoryOptions } from '../_utils/lookup';

const loading = ref(false);
const saving = ref(false);
const rows = ref<Department[]>([]);
const total = ref(0);
const editVisible = ref(false);
const isCreate = ref(false);

const query = reactive({
  departmentName: '',
  categoryCode: '',
  pageNum: 1,
  pageSize: 20,
});

const form = reactive<Department>({
  department_code: '',
  department_name: '',
  category_code: '',
  description: '',
  online_enabled: true,
  active: true,
});

const metrics = computed(() => [
  { title: '科室总数', value: total.value || rows.value.length, desc: '当前筛选结果', tone: 'primary' as const, icon: 'OfficeBuilding' },
  { title: '线上挂号', value: rows.value.filter(item => item.online_enabled).length, desc: '已开放预约', tone: 'success' as const, icon: 'CircleCheck' },
  { title: '停用科室', value: rows.value.filter(item => item.active === false).length, desc: '不参与业务', tone: 'warning' as const, icon: 'RemoveFilled' },
  { title: '科室分类', value: new Set(rows.value.map(item => item.category_code).filter(Boolean)).size, desc: '按分类归集', tone: 'info' as const, icon: 'CollectionTag' },
]);

function pickTotal(res: unknown, fallback: number) {
  const data = res as { total?: number; data?: { total?: number } };
  return data?.total ?? data?.data?.total ?? fallback;
}

function makeDepartmentCode() {
  return `DEPT-${Date.now()}`;
}

async function loadRows() {
  loading.value = true;
  try {
    const res = await listDepartments({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      departmentName: query.departmentName,
      categoryCode: query.categoryCode,
    });
    rows.value = pickRows<Department>(res);
    total.value = pickTotal(res, rows.value.length);
  }
  finally {
    loading.value = false;
  }
}

function resetForm(row?: Department) {
  form.department_code = row?.department_code || '';
  form.department_name = row?.department_name || '';
  form.category_code = row?.category_code || '';
  form.description = row?.description || '';
  form.online_enabled = row?.online_enabled ?? true;
  form.active = row?.active ?? true;
}

function openEditor(row?: Department) {
  isCreate.value = !row;
  resetForm(row);
  editVisible.value = true;
}

async function saveRow() {
  if (!form.department_name) {
    ElMessage.warning('请填写科室名称');
    return;
  }

  saving.value = true;
  try {
    if (isCreate.value) {
      form.department_code = form.department_code || makeDepartmentCode();
      await addDepartment({ ...form });
    }
    else
      await updateDepartment(form.department_code, { ...form });
    ElMessage.success('科室已保存');
    editVisible.value = false;
    await loadRows();
  }
  finally {
    saving.value = false;
  }
}

function resetFilters() {
  query.departmentName = '';
  query.categoryCode = '';
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
        <h1 class="medical-page__title">科室管理</h1>
        <p class="medical-page__desc">维护科室基础资料、分类信息和线上挂号开放状态。</p>
      </div>
      <div class="medical-actions">
        <el-button type="primary" @click="openEditor()">新增科室</el-button>
        <el-button :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <el-form class="medical-filter" :model="query" inline>
      <el-form-item label="科室名称">
        <el-input v-model="query.departmentName" clearable placeholder="输入科室名称" @keyup.enter="loadRows" />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="query.categoryCode" clearable filterable placeholder="全部分类">
          <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
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
        <h2 class="medical-section__title">科室列表</h2>
        <span class="medical-muted">共 {{ total || rows.length }} 条</span>
      </div>
      <div class="medical-section__body">
        <el-table v-loading="loading" :data="rows" class="medical-table" table-layout="fixed">
          <el-table-column prop="department_name" label="科室名称" min-width="160" />
          <el-table-column label="分类" width="130">
            <template #default="{ row }">{{ categoryLabel(row.category_code) }}</template>
          </el-table-column>
          <el-table-column label="线上挂号" width="110">
            <template #default="{ row }">
              <el-tag :type="row.online_enabled === false ? 'info' : 'success'" effect="light">
                {{ row.online_enabled === false ? '关闭' : '开启' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="row.active === false ? 'danger' : 'success'" effect="light">
                {{ row.active === false ? '停用' : '启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ displayValue(row.description) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="86">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="department-pagination">
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

    <el-dialog v-model="editVisible" :title="isCreate ? '新增科室' : '编辑科室'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="科室名称" required>
          <el-input v-model="form.department_name" placeholder="输入科室名称" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category_code" clearable filterable placeholder="选择分类">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="线上挂号">
          <el-switch v-model="form.online_enabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.active" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="填写运营说明" />
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

.department-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
