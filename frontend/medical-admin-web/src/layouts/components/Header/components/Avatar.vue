<!-- 头像 -->
<script setup lang="ts">
import { useUserStore } from '@/stores';

const userStore = useUserStore();
const src = computed(
  () => userStore.userInfo?.avatar ?? 'https://avatars.githubusercontent.com/u/32251822?s=96&v=4',
);

function handleCommand(command: string) {
  switch (command) {
    case 'profile':
      ElMessage.warning('暂未开放');
      break;
    case 'logout':
      ElMessageBox.confirm('退出登录不会丢失任何数据，你仍可以登录此账号。', '确认退出登录？', {
        confirmButtonText: '确认退出',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
        cancelButtonClass: 'el-button--info',
        roundButton: true,
        autofocus: false,
      })
        .then(async () => {
          // 在这里执行退出方法
          await userStore.logout();
          ElMessage({
            type: 'success',
          message: '退出成功',
        });
        })
        .catch(() => {});
      break;
    default:
      break;
  }
}
</script>

<template>
  <div class="avatar-container">
    <el-dropdown trigger="click" @command="handleCommand">
      <button class="avatar-trigger" type="button">
        <el-avatar :src="src" :size="28" fit="cover" shape="circle" />
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">
            账号信息
          </el-dropdown-item>
          <el-dropdown-item divided command="logout">
            退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped lang="scss">
.avatar-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 0;
}
</style>
