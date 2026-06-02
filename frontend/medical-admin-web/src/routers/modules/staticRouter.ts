import type { RouteRecordRaw } from 'vue-router';
import { HOME_URL } from '@/config';

export const layoutRouter: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: HOME_URL,
  },
  {
    path: '/medical',
    component: () => import('@/layouts/MedicalLayout/index.vue'),
    children: [
      {
        path: '',
        name: 'medicalHome',
        component: () => import('@/pages/medical/home/index.vue'),
        meta: {
          title: '医疗工作台',
          icon: 'FirstAidKit',
        },
      },
      {
        path: 'department',
        name: 'medicalDepartment',
        component: () => import('@/pages/medical/department/index.vue'),
        meta: {
          title: '科室管理',
          icon: 'OfficeBuilding',
        },
      },
      {
        path: 'doctor',
        name: 'medicalDoctor',
        component: () => import('@/pages/medical/doctor/index.vue'),
        meta: {
          title: '医生查询',
          icon: 'UserFilled',
        },
      },
      {
        path: 'patient',
        name: 'medicalPatient',
        component: () => import('@/pages/medical/patient/index.vue'),
        meta: {
          title: '患者管理',
          icon: 'User',
        },
      },
      {
        path: 'platformUser',
        name: 'medicalPlatformUser',
        component: () => import('@/pages/medical/platformUser/index.vue'),
        meta: {
          title: '平台用户',
          icon: 'Avatar',
        },
      },
      {
        path: 'binding',
        name: 'medicalBinding',
        component: () => import('@/pages/medical/binding/index.vue'),
        meta: {
          title: '就诊人绑定',
          icon: 'Connection',
        },
      },
      {
        path: 'slot',
        name: 'medicalSlot',
        component: () => import('@/pages/medical/slot/index.vue'),
        meta: {
          title: '可预约号源',
          icon: 'Calendar',
        },
      },
      {
        path: 'role-monitor',
        name: 'medicalRoleMonitor',
        component: () => import('@/pages/medical/role-monitor/index.vue'),
        meta: {
          title: '医患监控',
          icon: 'TrendCharts',
        },
      },
      {
        path: 'order-trace',
        name: 'medicalOrderTrace',
        component: () => import('@/pages/medical/order-trace/index.vue'),
        meta: {
          title: '订单追踪',
          icon: 'Tickets',
        },
      },
      {
        path: 'knowledge-doc',
        name: 'medicalKnowledgeDoc',
        component: () => import('@/pages/medical/knowledge-doc/index.vue'),
        meta: {
          title: '知识库文档管理',
          icon: 'Collection',
        },
      },
      {
        path: 'registration',
        name: 'medicalRegistration',
        component: () => import('@/pages/medical/registration/index.vue'),
        meta: {
          title: '挂号订单',
          icon: 'Tickets',
        },
      },
      {
        path: 'monitor',
        name: 'medicalMonitor',
        component: () => import('@/pages/medical/monitor/index.vue'),
        meta: {
          title: '业务监控',
          icon: 'TrendCharts',
        },
      },
      {
        path: 'exception',
        name: 'medicalException',
        component: () => import('@/pages/medical/exception/index.vue'),
        meta: {
          title: '异常处理',
          icon: 'WarningFilled',
        },
      },
      {
        path: 'trace',
        name: 'medicalTrace',
        component: () => import('@/pages/medical/trace/index.vue'),
        meta: {
          title: '链路追踪',
          icon: 'Share',
        },
      },
      {
        path: 'knowledge',
        name: 'medicalKnowledge',
        component: () => import('@/pages/medical/knowledge/index.vue'),
        meta: {
          title: '知识库治理',
          icon: 'Collection',
        },
      },
    ],
  },
];

export const staticRouter: RouteRecordRaw[] = [];

export const errorRouter = [
  {
    path: '/403',
    name: '403',
    component: () => import('@/pages/error/403.vue'),
    meta: {
      title: '403页面',
      enName: '403 Page',
      icon: 'QuestionFilled',
      isHide: '1',
      isLink: '1',
      isKeepAlive: '0',
      isFull: '1',
      isAffix: '1',
    },
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/pages/error/404.vue'),
    meta: {
      title: '404页面',
      enName: '404 Page',
      icon: 'CircleCloseFilled',
      isHide: '1',
      isLink: '1',
      isKeepAlive: '0',
      isFull: '1',
      isAffix: '1',
    },
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/pages/error/404.vue'),
  },
];
