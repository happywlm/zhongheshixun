-- =============================================================
-- db-upgrade-from-v1.sql
-- 数据库增量升级脚本：从 V1.0 升级到 V1.2.0 (含 V2_0 RBAC + V2_1 schema 修复)
-- 日期: 2026-07-12
--
-- 适用场景:
--   已有运行中的 training 数据库 (16 张业务表已就绪)
--   需要补齐 RBAC 4 张权限表 + 应用 V2_1 schema 修复
--
-- 全新部署请用:
--   docs/database.sql (一体化 16+4 张表 + V2_1 修复)
--
-- 使用方法:
--   mysql -uroot -proot training < docs/db-upgrade-from-v1.sql
--
-- 已执行过本脚本的可以重复运行(全部 IF NOT EXISTS / 检查后 ALTER)
--
-- 内容:
--   1) V2_0 RBAC 权限重构（建 4 张表 + sys_user 加 role_id 字段）
--   2) V2_1 schema 修复（teacher.user_id 允许 NULL + question.question_type 默认 1）
--   3) 验证脚本（可选）
-- =============================================================

SET NAMES utf8mb4;
USE training;

-- ============================================================
-- 1) V2_0 RBAC 权限重构
-- ============================================================

-- 1.1) sys_role 角色字典
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(50)  NOT NULL UNIQUE         COMMENT '角色编码：ADMIN/TEACHER/STUDENT',
    role_name   VARCHAR(50)  NOT NULL                COMMENT '角色显示名',
    description VARCHAR(200)          DEFAULT NULL   COMMENT '角色说明',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0禁用 1启用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色字典表';

-- 1.2) sys_permission 权限字典
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    perm_code   VARCHAR(100) NOT NULL UNIQUE         COMMENT '权限编码：module:action',
    perm_name   VARCHAR(100) NOT NULL                COMMENT '权限显示名',
    description VARCHAR(200)          DEFAULT NULL   COMMENT '权限说明',
    module      VARCHAR(50)           DEFAULT NULL   COMMENT '所属模块分组（前端分组展示用）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1已删',
    PRIMARY KEY (id),
    KEY idx_module (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限字典表';

-- 1.3) sys_role_permission 角色-权限关联
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT    NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id       BIGINT    NOT NULL                COMMENT 'FK -> sys_role.id',
    permission_id BIGINT    NOT NULL                COMMENT 'FK -> sys_permission.id',
    create_time   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_role_id (role_id),
    KEY idx_perm_id (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-权限关联表';

-- 1.4) sys_user 加 role_id 列 (idempotent, 若已有则跳过)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'training' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN role_id BIGINT DEFAULT NULL COMMENT ''FK -> sys_role.id'' AFTER status',
    'SELECT ''sys_user.role_id 已存在,跳过'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.5) sys_user.role_id 索引
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'training' AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_role_id');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE sys_user ADD KEY idx_role_id (role_id)',
    'SELECT ''sys_user.idx_role_id 已存在,跳过'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.6) 字典数据由应用启动时 RbacDataInitializer 自动初始化,无需手动 INSERT
--     若想立即初始化,重启 Spring Boot 即可

-- ============================================================
-- 2) V2_1 schema 修复 (M12 联调期,2026-07-12)
-- ============================================================

-- 2.1) teacher.user_id 允许 NULL (独立维护讲师档案场景)
SET @col_nullable = (SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'training' AND TABLE_NAME = 'teacher' AND COLUMN_NAME = 'user_id');
SET @sql = IF(@col_nullable = 'NO',
    'ALTER TABLE teacher MODIFY COLUMN user_id BIGINT DEFAULT NULL COMMENT ''关联用户ID(可空,允许讲师独立维护档案)''',
    'SELECT ''teacher.user_id 已是 NULLABLE,跳过'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2) question.question_type 允许 NULL + 默认 1
SET @col_default = (SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'training' AND TABLE_NAME = 'question' AND COLUMN_NAME = 'question_type');
SET @sql = IF(@col_default IS NULL,
    'ALTER TABLE question MODIFY COLUMN question_type TINYINT DEFAULT 1 COMMENT ''类型:1 单选 2 多选 3 判断 4 填空 5 问答''',
    'SELECT ''question.question_type 已有默认值,跳过'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3) 验证 (可选,确认 schema 状态)
-- ============================================================
SELECT 'V2_0 RBAC 表' AS check_group;
SELECT TABLE_NAME, TABLE_COMMENT
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'training' AND TABLE_NAME IN ('sys_role', 'sys_permission', 'sys_role_permission');

SELECT 'V2_1 schema 修复结果' AS check_group;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'training'
  AND ((TABLE_NAME = 'teacher' AND COLUMN_NAME = 'user_id')
    OR (TABLE_NAME = 'question' AND COLUMN_NAME = 'question_type'));

-- 期望输出:
-- teacher  | user_id       | bigint  | YES | NULL
-- question | question_type | tinyint | YES | 1

-- ============================================================
-- 4) 完成后
-- ============================================================
SELECT '✅ 升级完成!请重启应用 (9898/9899) 加载新 schema.' AS done;
