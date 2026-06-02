<script setup lang="ts">
import '@/pages/medical/_styles/medical.scss';
import MedicalAside from './components/MedicalAside.vue';
import MedicalHeader from './components/MedicalHeader.vue';
</script>

<template>
  <div class="medical-layout">
    <MedicalAside />
    <div class="medical-layout__main">
      <MedicalHeader />
      <main class="medical-layout__content">
        <router-view v-slot="{ Component }">
          <Suspense>
            <component :is="Component" />
            <template #fallback>
              <div class="medical-page-loading">
                <span v-for="item in 6" :key="item" />
              </div>
            </template>
          </Suspense>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.medical-layout {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--medical-bg);
}

.medical-layout__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.medical-layout__content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
}

.medical-page-loading {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr));
  gap: 16px;
  padding: 18px;

  span {
    min-height: 96px;
    border: 1px solid #e5e9f2;
    border-radius: 8px;
    background: linear-gradient(90deg, #eef2f7 25%, #f8fafc 37%, #eef2f7 63%);
    background-size: 400% 100%;
    animation: medical-page-loading-shimmer 1.2s ease-in-out infinite;
  }
}

@keyframes medical-page-loading-shimmer {
  0% {
    background-position: 100% 0;
  }

  100% {
    background-position: 0 0;
  }
}

@media (max-width: 900px) {
  .medical-page-loading {
    grid-template-columns: 1fr;
  }
}
</style>
