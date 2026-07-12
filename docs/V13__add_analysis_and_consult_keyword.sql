-- ============================================================
-- M13 数据库迁移脚本
-- 1. question 表新增 analysis 列（答案解析）
-- 2. 新增 consult_keyword 表（关键词转人工配置）
-- ============================================================

-- 1. question 表新增 analysis 列
ALTER TABLE `question`
    ADD COLUMN `analysis` TEXT NULL COMMENT '答案解析' AFTER `difficulty`;

-- 2. 新增 consult_keyword 表（关键词路由配置）
CREATE TABLE IF NOT EXISTS `consult_keyword` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `keyword`     VARCHAR(50)  NOT NULL COMMENT '触发关键词',
    `action`      VARCHAR(20)  NOT NULL DEFAULT 'to_human' COMMENT '动作：to_human=转人工 / to_ai=转AI',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序（小的优先）',
    `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0禁用 1启用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    INDEX `idx_keyword` (`keyword`),
    INDEX `idx_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询关键词路由配置';

-- 预置转人工关键词
INSERT INTO `consult_keyword` (`keyword`, `action`, `sort_order`, `enabled`) VALUES
    ('转人工',   'to_human', 1, 1),
    ('找老师',   'to_human', 2, 1),
    ('人工客服', 'to_human', 3, 1),
    ('真人',     'to_human', 4, 1),
    ('人工',     'to_human', 5, 1),
    ('客服',     'to_human', 6, 1);
