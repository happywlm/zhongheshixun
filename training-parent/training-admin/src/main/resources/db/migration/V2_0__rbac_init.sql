-- =============================================================
-- V2_0__rbac_init.sql
-- RBAC 权限重构 Phase 1：数据库层
-- 创建时间：2026-07-08
--
-- 说明：本脚本为参考脚本，由 DBA / 维护者手工执行。
--       应用启动时 RbacDataInitializer 会自动初始化字典数据，
--       无需手工 INSERT 字典。本脚本仅包含 DDL 与 sys_user 迁移。
-- =============================================================

-- -------------------------------------------------------------
-- 1. 新建 sys_role 表（角色字典）
-- -------------------------------------------------------------
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

-- -------------------------------------------------------------
-- 2. 新建 sys_permission 表（权限字典）
-- -------------------------------------------------------------
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

-- -------------------------------------------------------------
-- 3. 新建 sys_role_permission 表（角色-权限关联）
-- -------------------------------------------------------------
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

-- -------------------------------------------------------------
-- 4. 改造 sys_user：加 role_id 列（允许 NULL 便于平滑迁移）
-- -------------------------------------------------------------
ALTER TABLE sys_user
    ADD COLUMN role_id BIGINT DEFAULT NULL COMMENT 'FK -> sys_role.id' AFTER status;
ALTER TABLE sys_user
    ADD KEY idx_role_id (role_id);

-- -------------------------------------------------------------
-- 5. 数据迁移：role 字符串 -> role_id 数值
--    前提：已通过 RbacDataInitializer 插入 3 个角色（ADMIN=1, TEACHER=2, STUDENT=3）
-- -------------------------------------------------------------
-- UPDATE sys_user SET role_id = CASE role
--     WHEN 'admin'   THEN 1
--     WHEN 'teacher' THEN 2
--     WHEN 'student' THEN 3
--     ELSE 3
-- END;

-- -------------------------------------------------------------
-- 6. 验证迁移结果（必须返回 0）
-- -------------------------------------------------------------
-- SELECT COUNT(*) AS missing_role_id FROM sys_user WHERE role_id IS NULL;

-- -------------------------------------------------------------
-- 7. 确认迁移成功后，删除旧 role 列（谨慎！不可回滚）
-- -------------------------------------------------------------
-- ALTER TABLE sys_user DROP COLUMN role;
