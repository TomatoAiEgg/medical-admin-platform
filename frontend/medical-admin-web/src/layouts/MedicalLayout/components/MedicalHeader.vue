<script setup lang="ts">
import { useRoute } from 'vue-router';
import { useUserStore } from '@/stores';
import Avatar from '@/layouts/components/Header/components/Avatar.vue';
import LoginBtn from '@/layouts/components/Header/components/LoginBtn.vue';

const route = useRoute();
const userStore = useUserStore();

const pageTitle = computed(() => String(route.meta.title || '医疗工作台'));
</script>

<template>
  <header class="medical-header">
    <div class="medical-header__left">
      <h1>{{ pageTitle }}</h1>
      <el-tag type="success" effect="light">
        开发环境
      </el-tag>
    </div>

    <div class="medical-header__right">
      <el-input class="medical-header__search" placeholder="搜索订单、患者、traceId" clearable>
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-button text circle>
        <el-icon><Bell /></el-icon>
      </el-button>
      <el-button text circle>
        <el-icon><QuestionFilled /></el-icon>
      </el-button>
      <el-button text circle>
        <el-icon><FullScreen /></el-icon>
      </el-button>
      <Avatar v-show="userStore.token" />
      <LoginBtn v-show="!userStore.token" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.medical-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--medical-header-height);
  padding: 0 24px;
  background: var(--medical-surface);
  border-bottom: 1px solid var(--medical-border);
}

.medical-header__left,
.medical-header__right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.medical-header__left h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  color: var(--medical-text);
}

.medical-header__search {
  width: 280px;
}

@media (max-width: 960px) {
  .medical-header__search {
    display: none;
  }
}
</style>
