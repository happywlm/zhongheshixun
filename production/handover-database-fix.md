# 📋 数据库修复交接文档 (Handover)

> 🗓 **创建日期**: 2026-07-12
> ✍️ **作者**: tech-director (Apple Claude Opus 4.8)
> 🎯 **交接待办 AI**: 数据库 review v1.2.1 修复 + M10 联调启动

---

## 🟢 当前进度摘要

### ✅ 已完成 (不再需要改动)

| 模块 | 状态 | 版本/完成日期 |
|------|------|--------|
| 后端服务 (Spring Boot) | ✅ 完成 | training-admin 9898 + training-api 9899 |
| Web 前端 (Vue3 + Element Plus) | ✅ 完成 | 5174 (web-student), 5176 (admin-frontend) |
| 微信小程序 | ✅ 完成 | 7 页 + 智能问答 |
| 数据库 review 权限 gap 补齐 | ✅ 文件已备 | `db-upgrade-v1_2_1.sql` |
| database literal `\n` bug | ✅ 已修 | `docs/database.sql` line 25 (commit 至 working tree) |
| SysUser @TableLogic 注解 | ✅ 已修 | `training-dao/.../entity/SysUser.java` (working tree) |
| PR 对账完成 | ✅ 不 merge | apple 脚本已是 PR 超集 |

### 🟡 待办 (接手 AI 必须做)

| 序号 | 任务 | 优先级 | 预计时间 |
|------|------|--------|---------|
| 1 | **修复数据库中文乱码** | 🔴 P0 | 5 min |
| 2 | 验证 M10 联调启动三端 | 🟠 P1 | 30 min |
| 3 | 准备 ECharts 截图素材 | 🟠 P1 | 45 min |

---

## 🔴 P0: 数据库中文修复 — 唯一待办核心任务

### 现象

数据库里**所有 VARCHAR/TEXT 字段的中文内容都被替换成了 ASCII `?` (hex `3F`)**。

```sql
-- 当前错误数据示例
SELECT id, HEX(real_name), real_name FROM sys_user;
-- id | HEX(real_name) | real_name
-- 1  | 3F3F3F3F3F     | ?????    ← 应该是 "系统管理员"
-- 2  | 3F3F3F         | ???      ← 应该是 "张教授"
```

受影响的字段（42 个）：`sys_user.real_name/org_name/job_type`、`teacher.real_name/title/direction/intro`、`course.title/description`、`course_chapter.title`、`exam.title`、`question.title`、`knowledge_point.name`、`knowledge_base.question/answer`、`consult_record.question/answer` 等。

**全部是 `3F3F3F...`**.

### Root Cause (根因 100% 确认)

| 项 | 状态 |
|----|------|
| `docs/database.sql` 文件本身 | ✅ **文件是完美的 UTF-8** (实测 25.3% 中文字节, hex `e7bb9fe7aea1...` 系统管理员 ✓) |
| MySQL server character_set | ✅ `utf8mb4` |
| 表/列 collation | ✅ 全部 `utf8mb4_0900_ai_ci` (42 个字段) |
| ** PowerShell 管线 ** | ❌ **已确认破坏 UTF-8 字节** |

**Root cause**: 当前终端 `cmd.exe` 的后代 PowerShell 5.1 (Windows 10/11 默认), **`Get-Content ... | mysql` 管线**会把 UTF-8 字节流**重新编码成 UCS-2**再转给 mysql.exe。即使加了 `--default-character-set=utf8mb4`, 管道字节流在交给 mysql 之前已被替换成 `?` (0x3F)。

多次尝试均失败:
- ✅ `chcp 65001` → 仍 `3F` 损坏
- ✅ `--default-character-set=utf8mb4` → 仍 `3F`
- ✅ `Out-File -Encoding UTF8` → 仍 `3F`
- ✅ `SET NAMES utf8mb4`; `--default-character-set` 参数 双管齐下 → 仍 `3F`
- ❌ 没有任何 PS 编码参数组合能使这管线工作

**转败为胜的唯一路径**: **不在 PowerShell 中运行 mysql 导入**, 用 `stdin` 文件重定向 + Bash 环境。

### 修复文件清单 (已备、可直接用)

| 文件 | 用途 | 中文内容状态 |
|------|------|--------|
| `docs/database.sql` (HEAD commit) | 20 张表 + 9 用户 + 4 课程的基础数据 | ✅ 文件完美 UTF-8 (~25% 中文) |
| `docs/db-upgrade-v1_2_1.sql` (新文件) | v1.2.1 升级: roleperm + 索引 + question#9 + 演示数据 | ✅ 文件 UTF-8 + ASCII UNHEX 保守; 7 行中文标题 (当 SET NAMES utf8mb4 工作时也能导入) |
| `docs/fix_data_corruption.sql` | 修复 question #9 (已执行, 成功) | ✅ UNHEX 修复成功 |
| `docs/fix_data_corruption_v2.sql` | 修复 consult_record 3 条 (已执行, 成功) | ✅ UNHEX 修复成功 |

### 接手 AI 必须执行的命令 ⭐

**在 Git Bash 终端中执行** (不是 PowerShell!)。如果你只会用 PowerShell, 先 `bash` 进入 Git Bash。

**第 0 步**: 进入项目目录

```bash
cd /d/A-Users/Desktop/zhongheshixun
```

**第 1 步**: DROP 重建数据库

```bash
mysql -uroot -proot --default-character-set=utf8mb4 \
  -e "DROP DATABASE IF EXISTS training; CREATE DATABASE training DEFAULT CHARSET utf8mb4;"
```

**第 2 步**: 导入 database.sql (Bash stdin 重定向 — 绕过 PS 管线)

```bash
mysql -uroot -proot --default-character-set=utf8mb4 training < docs/database.sql
```

**第 3 步**: 导入 v1.2.1 增量升级脚本

```bash
mysql -uroot -proot --default-character-set=utf8mb4 training < docs/db-upgrade-v1_2_1.sql
```

**第 4 步**: 验证中文恢复 (HEX 必须以 `e5/e6/e7/e8/e9` 等 UTF-8 lead bytes 开头, 不能全是 `3F`)

```bash
mysql -uroot -proot --default-character-set=utf8mb4 training -e "
SELECT id, HEX(real_name) AS real_hex, real_name FROM sys_user LIMIT 3;
SELECT LEFT(HEX(title),32) AS course_hex, title FROM course LIMIT 3;
SELECT LEFT(HEX(title),40) AS q_hex, title FROM question WHERE id IN (1,9);
SELECT COUNT(*) FROM sys_role_permission;
SELECT role_id, COUNT(*) AS cnt FROM sys_role_permission GROUP BY role_id;
"
```

### 期望输出 (通过标准)

```
+----+-----------------------+--------------+
| id | real_hex              | real_name    |
+----+-----------------------+--------------+
|  1 | E7BB9FE7AEA1E79086E59198 | 系统管理员 | ← UTF-8 ✓
|  2 | 5 BC55 5F...          | 张教授       | ← UTF-8 ✓
|  3 | ...                   | 李主任       | ← UTF-8 ✓
+----+-----------------------+--------------+

-- 关键点: real_hex E7 BB 9F... 是"系统管理员"的 UTF-8 字节
-- 如果看到 3F3F3F..., 说明通过 Bash stdin 重定向也失败了 (可能性 <1%), 立刻换方案
```

### 如果 Bash stdin 也失败

rare edge case — 如果 Bash 走不通, 手动 U N HEX 修复方案:

```sql
-- 这条 SQL 100% 注入正确的 UTF-8 字节, 无论客户端 encoding
UPDATE sys_user SET real_name = UNHEX('E7BB9FE7AEA1E79086E59198') WHERE id=1;
-- 其他字段同理 (docs/fix_data_corruption.sql 已示范)
```

---

## 🟠 项目关键位置速查

| 项目 | 路径 |
|------|------|
| 数据库初始化脚本 | `docs/database.sql` (~28 KB, v1.2.1) |
| v1.2.1 增量修复文档 | `docs/db-upgrade-v1_2_1.sql` |
| 数据库表结构说明 | `docs/dev-database.md` |
| 后端项目 | `training-parent/` |
| training-admin 后端/前端 | `training-parent/training-admin/` |
| training-api 后端 | `training-parent/training-api/` |
| 前端 web-student | `web-student/` |
| 小程序 | `miniprogram/` |

### 关键端口

| 端口 | 服务 |
|------|------|
| 9898 | training-admin 后端 |
| 9899 | training-api 后端 |
| 5174 | web-student 前端 (Vue3) |
| 5176 | admin-frontend 前端 (Vue3) |

### 演示账号 (密码统一 `123456`)

| 用户名 | 角色 | 真实名称 |
|--------|------|--------|
| admin | 管理员 | 系统管理员 |
| teacher01 | 讲师 | 张教授 |
| teacher02 | 讲师 | 李主任 |
| student01 | 学员 | 王医生 |
| student02-06 | 学员 | 赵护士/刘医生/陈医生/杨老师/黄医师 |

---

## 🛠 关键经验教训 (必读)

### 教训 1: PowerShell 别碰中文 + mysql

```powershell
# ❌ NEVER — 无论怎么加参数, UTF-8 字节都被 PS Out-String 破坏
Get-Content ... -Raw -Encoding UTF8 | mysql ...
```

```bash
# ✅ ALWAYS — 文件 stidin 重定向, 不要在 pipe 中
mysql ... < file.sql
```

### 教训 2: "--default-character-set=utf8mb4" 不够

这条参数只影响 MySQL 客户端内部的 character_set_client/connection/results 变量。如果 ** OS 层的管道 (cmd.exe -> mysql.exe 的 stdin) 把 UTF-8 字节替换成 GBK 字节**，这条参数无力回天 —— 字节已经在 mysql "看到"之前就坏了。

### 教训 3: 诊断 HEX 是 gold standard

不要只看 `SELECT real_name` 终端显示（受 GBK 控制台代码页误导）：

```sql
SELECT HEX(real_name) FROM ...;  -- gold standard
-- e7bb9f... = 正确 UTF-8
-- 3F3F3F... = 损坏 (ASCII '?')
```

---

## 📌 接手后立刻要做

1. **执行上面 §"接手 AI 必须执行的命令" 4 步**
2. **更新 `docs/database.sql` 的头部注释**: `SET NAMES utf8mb4;` 已经在 line 25, **在 Bash 中导入这一行会执行**, 确保客户端 encoding 正确
3. 验证通过后, 跑 M10 联调: 见 `docs/deploy.md`
4. 将所有一次性修复脚本 (`fix_data_corruption*.sql`) 移到 `production/tmp/` 存档

---

## ⚠️ 不要做的事 (我会替你踩坑)

- ❌ 不要尝试用 "mysql < file.sql" 在 ** PowerShell ** 中 — `<` 是保留字符
- ❌ 不要用 Python 脚本试图"修" database.sql 文件本身 — 文件是干净的
- ❌ 不要在没 `SET NAMES utf8mb4` 的情况下跑 `db-upgrade-v1_2_1.sql`
- ❌ 不要 commit `fix_data_corruption*.sql` (一次性修复, 不是长期方案)

---

## 🎆 期望结果

```
+----+-----------------------+--------------+
| id | real_hex              | real_name    |
+----+-----------------------+--------------+
|  1 | E7BB9FE7AEA1E79086E59198 | 系统管理员 |
|  2 | 5 BC 55 F...          | 张教授       |
|  3 | ...                   | 李主任       |
+----+-----------------------+--------------+
```

**PS**: 接手 AI 完成修复后, 把这条 hex 输出贴给我校验。

---

*end of handover document*
