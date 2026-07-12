# 数据库更新指南

> 本目录包含 3 种数据库管理文件，**新部署者请按场景选其一**。

## 文件清单

| 文件 | 大小 | 用途 | 适用场景 |
|------|------|------|----------|
| `database.sql` | ~28 KB | **一体化初始化脚本**（16 张业务表 + 4 张 RBAC 表 + 9 个示例用户 + V2_1 修复） | **全新部署**（无数据） |
| `db-upgrade-from-v1.sql` | ~7 KB | **增量升级脚本**（V2_0 RBAC + V2_1 schema 修复，幂等） | 已有 16 张业务表，**补齐 RBAC + V2_1** |
| `training-admin/src/main/resources/db/migration/V*.sql` | Flyway migration | Spring Boot 启动时自动执行 | **Flyway 接管**（生产推荐） |

---

## 场景 1：全新部署（最常见）

```bash
# 1) 创建数据库
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS training DEFAULT CHARSET utf8mb4;"

# 2) 导入一体化脚本（含 V2_1 修复）
mysql -uroot -proot training < docs/database.sql

# 3) 验证
mysql -uroot -proot training -e "SHOW TABLES;"
# 期望: 20 张表 (16 业务 + 4 RBAC)

# 4) 导入题库（可选，80 道示例题）
mysql -uroot -proot training < docs/question-data-80.sql
```

完成后重启 Spring Boot（端口 9898/9899）→ `RbacDataInitializer` 会自动建 3 个角色 + 20 条权限 + 角色权限关联。

---

## 场景 2：已有数据库，增量升级（你当前的情况）

```bash
# 已有 16 张业务表（V1_0）但缺 RBAC 表 + V2_1 修复
mysql -uroot -proot training < docs/db-upgrade-from-v1.sql
```

脚本特性：
- ✅ **幂等**：可重复运行，IF NOT EXISTS / 检查后 ALTER
- ✅ **安全**：用 INFORMATION_SCHEMA 检查后再 ALTER，不会破坏数据
- ✅ **完整**：含 V2_0（4 张 RBAC 表 + sys_user 加 role_id）+ V2_1（teacher.user_id 允许 NULL、question.question_type 默认 1）
- ✅ **验证**：末尾有 SELECT 校验

完成后重启 Spring Boot。

---

## 场景 3：Flyway 接管（生产环境推荐）

如果希望 Spring Boot 启动时**自动**执行 schema 演进：

### 3.1 启用 Flyway

`application.yml`：
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true    # 已有 schema 时自动 baseline
    locations: classpath:db/migration
```

### 3.2 准备 baseline

如果你的数据库已经跑过 `database.sql` 或 `db-upgrade-from-v1.sql`：
- 启动时 Flyway 会自动建 `flyway_schema_history` 表
- `baseline-on-migrate=true` 会把当前 schema 标记为 V1_0 baseline
- 之后**只跑 V2_0 / V2_1**（已存在则跳过）

### 3.3 Flyway migration 文件

| 文件 | 说明 |
|------|------|
| `V1_0__init.sql` | 占位说明文件（DDL 权威定义在 database.sql） |
| `V2_0__rbac_init.sql` | RBAC 4 张表 + sys_user 加 role_id |
| `V2_1__fix_schema_align_entity.sql` | teacher.user_id 允许 NULL + question.question_type 默认 1 |

---

## 验证清单

跑完任意一种方案后，执行以下 SQL 确认：

```sql
-- 1) 表数量（应 ≥ 20）
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'training';

-- 2) V2_1 schema 修复（必须 YES + 1）
SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'training'
  AND ((TABLE_NAME = 'teacher' AND COLUMN_NAME = 'user_id')
    OR (TABLE_NAME = 'question' AND COLUMN_NAME = 'question_type'));
-- 期望:
-- teacher  | user_id       | YES | NULL
-- question | question_type | YES | 1

-- 3) V2_0 RBAC 表（应有 4 张）
SHOW TABLES LIKE 'sys_%';

-- 4) 角色数量（启动后应为 3）
SELECT COUNT(*) FROM sys_role;
-- 期望: 3 (ADMIN/TEACHER/STUDENT)

-- 5) 权限数量（启动后应 ≥ 20）
SELECT COUNT(*) FROM sys_permission;
-- 期望: ≥ 20
```

---

## 常见问题

### Q1: 我已跑了 database.sql，还要跑 db-upgrade-from-v1.sql 吗？
**不需要**。database.sql (v1.2.0) 已包含 V2_1 修复。

### Q2: 跑了 db-upgrade-from-v1.sql 还能再跑吗？
**可以**。脚本完全幂等（IF NOT EXISTS / 条件 ALTER），重复执行无副作用。

### Q3: Flyway 启动报 "checksum mismatch" 怎么办？
说明 flyway_schema_history 里记录的 checksum 和文件不一致。原因：手动改过 migration SQL。

**解决**：
```sql
-- 方式 A: 删掉问题记录，让 Flyway 重跑
DELETE FROM flyway_schema_history WHERE version = '2_1';

-- 方式 B: 重新计算 checksum
UPDATE flyway_schema_history SET checksum = NULL WHERE version = '2_1';
```

### Q4: 我用的是 navicat 导入 SQL，UTF-8 乱码？
SQL 开头有 `SET NAMES utf8mb4;` 一般不会乱码。如果乱码：
- 检查 navicat 连接编码：UTF-8
- 改用命令行：`mysql -uroot -proot --default-character-set=utf8mb4 training < xxx.sql`

---

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0.0 | 2026-07-07 | 初版 16 张业务表 |
| v1.1.0 | 2026-07-09 | 加 RBAC 4 张表 + 9 个示例用户 + 培训数据 |
| **v1.2.0** | **2026-07-12** | **teacher.user_id 允许 NULL + question.question_type 默认 1（M12 联调期修复）** |
