# 四川省基层卫生人员网络培训平台

基于 Spring Boot 2.7 + Vue 3 + 微信小程序的基层卫生人员继续教育培训平台，面向四川省 67 个民族县 3000 名基层卫生技术人员，提供 MOOC 学习、在线考试、离线学习、实时咨询等服务。

---

## 目录

- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [测试账号](#测试账号)
- [常见问题](#常见问题)
- [文档索引](#文档索引)
- [贡献指南](#贡献指南)

---

## 环境要求

在开始之前，请确保您的电脑已安装以下软件。

### 必装（核心运行）

| 软件 | 版本要求 | 用途 | 下载 |
|------|---------|------|------|
| **JDK** | 1.8+ | 运行 Spring Boot 后端 | https://www.oracle.com/java/technologies/downloads/ |
| **Maven** | 3.6+ | 构建后端项目 | https://maven.apache.org/download.cgi |
| **Node.js** | 16+ (推荐 18) | 构建和运行前端 | https://nodejs.org/ |
| **MySQL** | 8.0+ | 数据库 | https://dev.mysql.com/downloads/mysql/ |
| **Git** | 任意 | 克隆/同步代码 | https://git-scm.com/ |

### 选装（按需）

| 软件 | 用途 | 说明 |
|------|------|------|
| **Redis** 7.x | 缓存（可选） | 不装也能跑，系统会降级处理 |
| **Navicat / DBeaver** | 数据库可视化 | 推荐 DBeaver（免费） |
| **IDEA / VSCode** | 代码编辑器 | IDEA 用于 Java，VSCode 用于前端 |
| **微信开发者工具** | 运行小程序 | https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html |

### 验证环境

打开终端执行：

```bash
java -version        # 应显示 1.8 或更高
mvn -version         # 应显示 3.6 或更高
node -v              # 应显示 v16 或更高
npm -v               # 应显示 8 或更高
mysql --version      # 应显示 8.0 或更高
git --version        # 任意版本都行
```

如果都正常，再开始下面的步骤。

---

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/Quinnx-Tommo/zhongheshixun.git
cd zhongheshixun
```

> 💡 **不要用 ZIP 下载**（缺少 `.git/` 目录，无法后续同步更新）

### 2. 初始化数据库

```bash
# 登录 MySQL
mysql -u root -p

# 执行初始化脚本
source docs/database.sql

# 验证（应看到 17 张表）
show databases;
use training;
show tables;
```

**默认管理员账号**：`admin / 123456`（详见 `docs/database.sql` 头部说明）

### 3. 启动后端

```bash
# 进入后端工程
cd training-parent

# 第一次需要编译（下载依赖 + 打包，约 3-5 分钟）
mvn clean install -DskipTests

# 启动后台管理 API（端口 9898）
java -jar training-admin/target/training-admin-exec.jar

# 另开一个终端，启动业务 API（端口 9899）
java -jar training-api/target/training-api-exec.jar
```

**后端启动成功的标志**：
```
Started TrainingAdminApplication in 5.234 seconds (JVM running for 6.789)
Tomcat started on port(s): 9898 (http)
```

### 4. 启动后台管理前端（端口 5176）

```bash
cd training-admin-ui

# 第一次需要安装依赖（1-2 分钟）
npm install

# 启动开发服务器
npm run dev
```

浏览器访问：http://localhost:5176

### 5. 启动 PC 学员端（端口 5174）

```bash
cd web-student

# 安装依赖
npm install

# 启动
npm run dev
```

浏览器访问：http://localhost:5174

### 6. 启动微信小程序（可选）

1. 下载并安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)
2. 打开工具 → 导入项目
3. 项目目录：`zhongheshixun/miniprogram`
4. AppID：选择"测试号"（或填写您自己的）
5. 点击"确定"即可在模拟器中预览

---

## 测试账号

所有测试账号统一密码为 **`123456`**。

| 用户名 | 角色 | 说明 |
|--------|------|------|
| `admin` | 管理员 | 后台管理系统 |
| `teacher01` | 讲师 | 后台管理 + 学员端 |
| `teacher02` | 讲师 | 后台管理 + 学员端 |
| `student01` ~ `student06` | 学员 | PC 学员端 + 小程序 |

---

## 项目结构

```
zhongheshixun/
├── training-parent/                # Maven 后端父工程
│   ├── training-common/            # 公共层（实体、工具、统一响应）
│   ├── training-dao/               # 数据访问层（Mapper）
│   ├── training-service/           # 业务逻辑层
│   ├── training-admin/             # 后台管理后端（端口 9898）
│   └── training-api/               # 小程序/业务 API（端口 9899）
│
├── training-admin-ui/              # 后台管理前端（Vue 3 + Element Plus）
├── web-student/                    # PC 学员端（Vue 3 + Vite）
├── miniprogram/                    # 微信小程序（学员移动端）
│
├── docs/                           # 权威开发文档
│   ├── 设计文档.md                 # 架构 + 数据库 + API 设计
│   ├── 开发文档.md                 # 开发总览
│   ├── dev-backend.md              # 后端实现手册
│   ├── dev-frontend.md             # 后台前端手册
│   ├── dev-web-student.md          # PC 学员端手册
│   ├── dev-miniapp.md              # 小程序手册
│   ├── dev-database.md             # 数据库手册
│   ├── dev-api.md                  # API 接口清单
│   ├── deploy.md                   # 部署文档
│   ├── database.sql                # 数据库初始化脚本
│   └── 需求偏差说明.md             # 需求对照
│
├── 专业文档/                       # 正式交付的 11 份文档
│   ├── 1.配置管理计划书.doc
│   ├── 2.软件需求规约.doc
│   ├── 3.项目开发计划.doc
│   ├── 4.数据库设计说明书.doc
│   ├── 5.系统架构设计说明书.doc
│   ├── 6.测试计划.doc
│   ├── 7.测试用例.doc
│   ├── 8.测试日志.doc
│   ├── 9.项目例会纪要.docx
│   ├── 10.项目开发总结报告.doc
│   └── 11.项目问题跟踪表.xls
│
├── defense-materials/              # 答辩材料
├── powerDesigner/                  # PowerDesigner 建模（工具安装文件，未上传）
├── AGENTS.md                       # AI Agent 项目说明
├── CLAUDE.md                       # Claude Code 项目说明
├── README.md                       # 本文件
├── LICENSE
└── .gitignore                      # Git 忽略规则
```

---

## 常见问题

### Q1: `mvn install` 下载依赖很慢怎么办？

配置 Maven 阿里云镜像。编辑 `~/.m2/settings.xml`：

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <name>Aliyun Maven</name>
    <url>https://maven.aliyun.com/repository/public</url>
    <mirrorOf>central</mirrorOf>
  </mirror>
</mirrors>
```

### Q2: 启动后端报错"无法连接数据库"？

检查 `training-admin/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/training?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: 你的MySQL密码    # ← 改成你自己的
```

### Q3: 启动后端报错"端口 9898/9899 被占用"？

修改 `application.yml` 中的 `server.port`，或关闭占用端口的程序：

```bash
# Windows 查看端口占用
netstat -ano | findstr :9898
taskkill /PID 进程号 /F
```

### Q4: 前端启动后白屏/样式丢失？

清空缓存重启：

```bash
# 删除依赖和缓存
rm -rf node_modules package-lock.json
npm cache clean --force

# 重新安装
npm install
npm run dev
```

### Q5: 别人下载下来 `training-parent` 只有 348KB，正常吗？

✅ **完全正常**。`target/` 目录（Maven 编译产物，约 60MB）已被 `.gitignore` 排除，运行 `mvn install` 后会自动生成。

### Q6: GitHub 显示 404 怎么办？

确保访问的是小写 x 的 URL：https://github.com/Quinnx-Tommo/zhongheshixun
- 用户名是 `Quinnx-Tommo`（小写 x），不是 `QuinnX-Tommo`（大写 X）

### Q7: 我更新了代码，别人怎么拿到？

```bash
# 已克隆的用户
cd zhongheshixun
git pull

# 新用户
git clone https://github.com/Quinnx-Tommo/zhongheshixun.git
```

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [设计文档](docs/设计文档.md) | 架构、数据库、API 设计总览 |
| [开发文档](docs/开发文档.md) | 开发总览 + 分工 |
| [dev-backend.md](docs/dev-backend.md) | 后端实现手册 |
| [dev-frontend.md](docs/dev-frontend.md) | 后台前端手册 |
| [dev-web-student.md](docs/dev-web-student.md) | PC 学员端手册 |
| [dev-miniapp.md](docs/dev-miniapp.md) | 小程序手册 |
| [dev-database.md](docs/dev-database.md) | 数据库手册 |
| [dev-api.md](docs/dev-api.md) | API 接口清单 |
| [deploy.md](docs/deploy.md) | 部署文档 |
| [需求偏差说明](docs/需求偏差说明.md) | 需求对照（v2.0 100% 覆盖） |
| [项目启动指南](docs/项目启动指南.md) | 环境准备 + 启动 |

---

## 贡献指南

欢迎贡献！请按以下流程：

```bash
# 1. Fork 本仓库（在你的 GitHub 账号下创建一份副本）

# 2. 克隆你自己的 Fork
git clone https://github.com/你的用户名/zhongheshixun.git

# 3. 创建新分支
git checkout -b feat/your-feature

# 4. 修改代码后提交
git add .
git commit -m "feat: 描述你的修改"

# 5. 推送到你自己的 Fork
git push -u origin feat/your-feature

# 6. 在 GitHub 网页提 Pull Request
# 访问 https://github.com/Quinnx-Tommo/zhongheshixun/pulls
```

### 提交信息规范（Conventional Commits）

| 前缀 | 用途 | 示例 |
|------|------|------|
| `feat:` | 新功能 | `feat: 新增课程章节进度接口` |
| `fix:` | 修复 bug | `fix: 修复考试多选题答案顺序问题` |
| `docs:` | 文档变更 | `docs: 更新部署文档端口` |
| `refactor:` | 重构 | `refactor: 抽取 RBAC 权限校验` |
| `style:` | 格式调整 | `style: 统一 ESLint 代码风格` |
| `test:` | 测试 | `test: 补充考试模块单元测试` |
| `chore:` | 杂项 | `chore: 更新 .gitignore` |

---

## 技术栈

| 模块 | 技术 | 版本 |
|------|------|------|
| 后端 | Spring Boot | 2.7.18 |
| ORM | MyBatis-Plus | 3.5.3 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x（可选） |
| 认证 | JWT | jjwt 0.11.5 |
| 后台前端 | Vue 3 + TypeScript + Element Plus | — |
| PC 学员端 | Vue 3 + Vite | — |
| 移动端 | 微信小程序原生 | — |

---

## 许可证

仅用于毕设演示，未经授权不得商用。

---

## 联系方式

- **GitHub**: https://github.com/Quinnx-Tommo
- **Email**: 3511450008@qq.com
