# Medical Admin Platform

医疗运营后台管理平台，当前结构已经从旧混合工程整理为独立医疗后台项目。

## 项目结构

```text
medical-admin-platform/
  backend/
    medical-admin-server/   Spring Boot 启动入口与应用配置
    medical-business/       挂号、号源、患者、医生、异常处理、知识库治理业务
    medical-system/         用户、角色、菜单、权限、字典、租户等后台基础能力
    medical-common/         Web、Redis、MyBatis、Sa-Token、安全、日志、Excel 等通用能力
  frontend/
    medical-admin-web/      Vue 3 医疗运营后台前端
  database/                 初始化、升级、测试数据和注释 SQL
  docs/                     开发方案、运行说明和项目记录
```

## 后端

```bash
mvn -DskipTests validate
mvn -pl backend/medical-admin-server -am -DskipTests compile
```

Spring Boot 入口：

```text
com.smartclinic.MedicalAdminApplication
```

## 前端

```bash
cd frontend/medical-admin-web
npm install --legacy-peer-deps
npm run dev
```

默认访问：

```text
http://127.0.0.1:5173/medical
```
