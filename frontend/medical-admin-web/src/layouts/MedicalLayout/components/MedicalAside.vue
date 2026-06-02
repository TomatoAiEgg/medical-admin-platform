<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';

interface MedicalMenuItem {
  title: string;
  path: string;
  icon: string;
}

interface MedicalMenuGroup {
  title: string;
  items: MedicalMenuItem[];
}

const route = useRoute();
const router = useRouter();

const groups: MedicalMenuGroup[] = [
  {
    title: '工作台',
    items: [
      { title: '医疗工作台', path: '/medical', icon: 'DataAnalysis' },
    ],
  },
  {
    title: '订单管理',
    items: [
      { title: '挂号订单', path: '/medical/registration', icon: 'Tickets' },
      { title: '订单追踪', path: '/medical/order-trace', icon: 'Share' },
      { title: '异常处理', path: '/medical/exception', icon: 'WarningFilled' },
    ],
  },
  {
    title: '医患管理',
    items: [
      { title: '科室管理', path: '/medical/department', icon: 'OfficeBuilding' },
      { title: '医生查询', path: '/medical/doctor', icon: 'UserFilled' },
      { title: '患者管理', path: '/medical/patient', icon: 'User' },
      { title: '平台用户', path: '/medical/platformUser', icon: 'Avatar' },
      { title: '就诊人绑定', path: '/medical/binding', icon: 'Connection' },
    ],
  },
  {
    title: '号源管理',
    items: [
      { title: '可预约号源', path: '/medical/slot', icon: 'Calendar' },
    ],
  },
  {
    title: '知识库管理',
    items: [
      { title: '知识库文档管理', path: '/medical/knowledge-doc', icon: 'Collection' },
      { title: '知识库治理', path: '/medical/knowledge', icon: 'Search' },
    ],
  },
  {
    title: '监控审计',
    items: [
      { title: '业务监控', path: '/medical/monitor', icon: 'Monitor' },
      { title: '医患监控', path: '/medical/role-monitor', icon: 'TrendCharts' },
      { title: '链路追踪', path: '/medical/trace', icon: 'Link' },
    ],
  },
];

function isActive(path: string) {
  return route.path === path;
}

function go(path: string) {
  router.push(path);
}
</script>

<template>
  <aside class="medical-aside">
    <button class="medical-brand" type="button" @click="go('/medical')">
      <span class="medical-brand__icon">
        <el-icon><FirstAidKit /></el-icon>
      </span>
      <span>
        <strong>医疗运营后台</strong>
        <small>智能挂号管理</small>
      </span>
    </button>

    <nav class="medical-nav">
      <section v-for="group in groups" :key="group.title" class="medical-nav__group">
        <div class="medical-nav__title">
          {{ group.title }}
        </div>
        <button
          v-for="item in group.items"
          :key="item.path"
          type="button"
          class="medical-nav__item"
          :class="{ active: isActive(item.path) }"
          @click="go(item.path)"
        >
          <el-icon>
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.title }}</span>
        </button>
      </section>
    </nav>
  </aside>
</template>

<style scoped lang="scss">
.medical-aside {
  display: flex;
  flex-direction: column;
  width: var(--medical-sidebar-width);
  height: 100vh;
  background: var(--medical-surface);
  border-right: 1px solid var(--medical-border);
}

.medical-brand {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: var(--medical-header-height);
  padding: 0 18px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--medical-border);
}

.medical-brand__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #ffffff;
  background: var(--medical-primary);
  border-radius: 50%;
}

.medical-brand strong,
.medical-brand small {
  display: block;
}

.medical-brand strong {
  font-size: 16px;
  line-height: 22px;
  color: var(--medical-primary);
}

.medical-brand small {
  margin-top: 1px;
  font-size: 12px;
  color: var(--medical-text-muted);
}

.medical-nav {
  flex: 1;
  padding: 12px 8px 18px;
  overflow-y: auto;
}

.medical-nav__group {
  margin-bottom: 12px;
}

.medical-nav__title {
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  color: var(--medical-text-muted);
}

.medical-nav__item {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  height: 36px;
  padding: 0 10px;
  margin-bottom: 3px;
  font-size: 14px;
  color: var(--medical-text-secondary);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--medical-radius);
}

.medical-nav__item:hover,
.medical-nav__item.active {
  color: var(--medical-primary);
  background: var(--medical-primary-soft);
}
</style>
