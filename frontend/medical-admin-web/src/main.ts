import {
  ArrowDown,
  ArrowLeftBold,
  ArrowRight,
  ArrowRightBold,
  Avatar,
  Bell,
  Calendar,
  Check,
  CircleCheck,
  CircleCheckFilled,
  CircleCloseFilled,
  Clock,
  Collection,
  CollectionTag,
  Connection,
  DataAnalysis,
  Document,
  DocumentCopy,
  ElementPlus,
  FirstAidKit,
  Histogram,
  InfoFilled,
  Link,
  Loading,
  Lock,
  Message,
  Monitor,
  OfficeBuilding,
  Operation,
  Paperclip,
  Plus,
  Refresh,
  RemoveFilled,
  Search,
  Service,
  Share,
  Tickets,
  Tools,
  TrendCharts,
  Unlock,
  Upload,
  User,
  UserFilled,
  WarningFilled,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { createApp } from 'vue';
import App from './App.vue';
import router from './routers';
import store from './stores';
import './styles/index.scss';
import 'virtual:uno.css';
import 'element-plus/dist/index.css';
// SVG插件配置
import 'virtual:svg-icons-register';

const app = createApp(App);
const globalIcons = {
  ArrowDown,
  ArrowLeftBold,
  ArrowRight,
  ArrowRightBold,
  Avatar,
  Bell,
  Calendar,
  Check,
  CircleCheck,
  CircleCheckFilled,
  CircleCloseFilled,
  Clock,
  Collection,
  CollectionTag,
  Connection,
  DataAnalysis,
  Document,
  DocumentCopy,
  ElementPlus,
  FirstAidKit,
  Histogram,
  InfoFilled,
  Link,
  Loading,
  Lock,
  Message,
  Monitor,
  OfficeBuilding,
  Operation,
  Paperclip,
  Plus,
  Refresh,
  RemoveFilled,
  Search,
  Service,
  Share,
  Tickets,
  Tools,
  TrendCharts,
  Unlock,
  Upload,
  User,
  UserFilled,
  WarningFilled,
};

app.use(store);
app.use(router);
app.use(ElMessage);
for (const [key, component] of Object.entries(globalIcons)) {
  app.component(key, component);
}

app.mount('#app');
