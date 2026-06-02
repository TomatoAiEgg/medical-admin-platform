<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const isMedicalRoute = computed(() => route.path.startsWith('/medical'));
</script>

<template>
  <router-view v-slot="{ Component }">
    <Suspense>
      <component :is="Component" />
      <template #fallback>
        <div class="app-route-loading" :class="{ 'app-route-loading--medical': isMedicalRoute }">
          <aside v-if="isMedicalRoute" class="app-route-loading__aside">
            <div class="app-route-loading__brand" />
            <span v-for="item in 8" :key="item" />
          </aside>
          <main class="app-route-loading__main">
            <div class="app-route-loading__bar" />
            <section class="app-route-loading__content">
              <span v-for="item in 4" :key="item" />
            </section>
          </main>
        </div>
      </template>
    </Suspense>
  </router-view>
</template>

<style scoped lang="scss">
.app-route-loading {
  display: flex;
  width: 100vw;
  min-height: 100vh;
  background: #f5f7fb;
}

.app-route-loading__aside {
  width: 228px;
  padding: 18px 16px;
  background: #ffffff;
  border-right: 1px solid #e5e9f2;

  span {
    display: block;
    height: 36px;
    margin-top: 12px;
    border-radius: 8px;
    background: linear-gradient(90deg, #eef2f7 25%, #f8fafc 37%, #eef2f7 63%);
    background-size: 400% 100%;
    animation: route-loading-shimmer 1.2s ease-in-out infinite;
  }
}

.app-route-loading__brand {
  width: 148px;
  height: 28px;
  margin-bottom: 22px;
  border-radius: 8px;
  background: #e7eef8;
}

.app-route-loading__main {
  flex: 1;
  min-width: 0;
}

.app-route-loading__bar {
  height: 64px;
  background: #ffffff;
  border-bottom: 1px solid #e5e9f2;
}

.app-route-loading__content {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 16px;
  padding: 18px;

  span {
    height: 108px;
    border: 1px solid #e5e9f2;
    border-radius: 8px;
    background: linear-gradient(90deg, #eef2f7 25%, #f8fafc 37%, #eef2f7 63%);
    background-size: 400% 100%;
    animation: route-loading-shimmer 1.2s ease-in-out infinite;
  }
}

.app-route-loading:not(.app-route-loading--medical) {
  align-items: center;
  justify-content: center;

  .app-route-loading__main {
    width: min(420px, calc(100vw - 32px));
    flex: none;
  }

  .app-route-loading__bar {
    height: 12px;
    border: 0;
    border-radius: 999px;
    background: #dfe7f3;
  }

  .app-route-loading__content {
    grid-template-columns: 1fr;
    padding: 16px 0 0;
  }
}

@keyframes route-loading-shimmer {
  0% {
    background-position: 100% 0;
  }

  100% {
    background-position: 0 0;
  }
}

@media (max-width: 768px) {
  .app-route-loading__aside {
    display: none;
  }

  .app-route-loading__content {
    grid-template-columns: 1fr;
  }
}
</style>
