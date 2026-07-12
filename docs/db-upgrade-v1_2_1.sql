-- ============================================================
-- v1.2.0 -> v1.2.1 增量升级脚本
-- 2026-07-12 database review 修复清单
--
-- 重要: 必须在 utf8mb4 character_set 下导入 !
--   推荐 (Git Bash / WSL): mysql -uroot -proot --default-character-set=utf8mb4 training < docs/db-upgrade-v1_2_1.sql
--   ⚠️ 切勿使用 PowerShell 管线 (Get-Content | mysql), 会把 UTF-8 字节破坏成 ASCII '?'
--   ⚠️ PowerShell 下请改用 Git Bash: bash -c "mysql ... < docs/db-upgrade-v1_2_1.sql"
--
-- 修复内容 (P0-P3):
--   [P0] roleperm: STUDENT 权限 2->10 条; TEACHER 权限 13->17 条 (补齐 consult+stats+resource)
--   [P0] roleperm: knowledge:read/write 从 0 绑定提升到 100% 绑定 (避免菜单悬空)
--   [P1] 补齐 15 处高频查询索引 (course_chapter/course_enroll/study_record/exam_paper/
--        exam_answer/consult_record/resource_file/plan_course/teacher)
--   [P2] question #9 去重 (高血压诊断标准 -> 高血压控制目标)
--   [P2+] 演示数据均衡化 (course_enroll student02-06 / study_record / consult_record)
--   [P3] course.offline_filename 加列对齐设计文档
--   [P3] consult_record.is_auto 注释归一
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;
USE training;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. roleperm 整段替换 (line 380 的 INSERT...VALUES 单行 -> 47 行 VALUES 单条 INSERTs)
--    使用临时表策略: rename -> drop -> reinsert
-- ============================================================

-- (a) 清空现有绑定
TRUNCATE TABLE sys_role_permission;

-- (b) 重新插入
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
-- (1) ADMIN: 全 20 条
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),
(1,11),(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,19),(1,20),
-- (2) TEACHER: 课程+章节+知识+题目+考试+咨询+计划+讲师+资源+统计 = 17 条
(2,1),(2,2),(2,3),(2,4),(2,5),(2,6),(2,7),(2,8),(2,9),(2,10),
(2,11),(2,12),(2,13),(2,16),(2,17),(2,18),(2,20),
-- (3) STUDENT: 只读 + consult:write (提问) + resource = 10 条
(3,1),(3,3),(3,5),(3,7),(3,9),(3,11),(3,12),(3,13),(3,16),(3,20);

-- ============================================================
-- 2. 索引补齐
-- ============================================================
ALTER TABLE sys_user     ADD KEY idx_user_role (role_id);
ALTER TABLE teacher     ADD KEY idx_teacher_user (user_id);
ALTER TABLE course       ADD KEY idx_course_teacher (teacher_id);
ALTER TABLE course_chapter ADD KEY idx_chapter_course (course_id);
ALTER TABLE course_enroll ADD KEY idx_enroll_student (student_id), ADD KEY idx_enroll_course (course_id);
ALTER TABLE study_record  ADD KEY idx_study_student (student_id), ADD KEY idx_study_chapter (chapter_id);
ALTER TABLE knowledge_point ADD KEY idx_kp_course (course_id);
ALTER TABLE exam_paper    ADD KEY idx_paper_exam (exam_id);
ALTER TABLE exam_answer   ADD KEY idx_answer_record (record_id);
ALTER TABLE consult_record ADD KEY idx_consult_student (student_id);
ALTER TABLE resource_file ADD KEY idx_resource_course (course_id);
ALTER TABLE plan_course   ADD KEY idx_plan_course_plan (plan_id), ADD KEY idx_plan_course_course (course_id);

-- ============================================================
-- 3. question #9 去重 (高血压诊断标准 -> 高血压控制目标)
-- ============================================================
UPDATE question SET
    title = '高血压患者血压控制目标一般为＜（）mmHg',
    options = '["A. 130/85","B. 140/90","C. 150/95","D. 160/100"]',
    answer = 'B'
WHERE id = 9;

-- ============================================================
-- 4. course.offline_filename 加列
-- ============================================================
ALTER TABLE course ADD COLUMN offline_filename VARCHAR(255) DEFAULT NULL
    COMMENT '离线学习 ZIP 文件名(NULL 表示不可下载)' AFTER offline_flag;

-- ============================================================
-- 5. 演示数据均衡化 (ECharts/联调素材)
-- ============================================================
-- course_enroll: student02-06 每人至少报名 1 门课程
INSERT IGNORE INTO course_enroll (student_id, course_id) VALUES
(5,1),(5,2),   -- student02: 报名课程 1+2
(6,1),(6,3),   -- student03: 报名课程 1+3
(7,2),         -- student04: 报名课程 2
(8,1),         -- student05: 报名课程 1
(9,4);         -- student06: 报名课程 4

-- study_record: 10 条学习记录用于 ECharts 进度展示
INSERT INTO study_record (student_id,course_id,chapter_id,progress,study_duration,last_position,completed) VALUES
(4,1,1, 100, 1800, 1800, 1),
(4,1,2, 100, 2400, 2400, 1),
(4,1,3,  60, 1200, 1200, 0),
(4,2,4, 100, 1500, 1500, 1),
(5,1,1, 100, 1700, 1700, 1),
(5,1,2,  30,  720,  720, 0),
(6,1,1, 100, 1800, 1800, 1),
(6,3,5,  80, 1680, 1680, 0),
(7,2,4,  50,  750,  750, 0),
(8,1,1, 100, 1800, 1800, 1);

-- exam_record / paper: student02 进行中考试 (status=0 未提交 用于考试中心列表演示)
INSERT INTO exam_record (id,student_id,exam_id,paper_id,score,status,start_time,submit_time) VALUES
(2, 5, 2, NULL, 0, 0, '2026-07-09 16:00:00', NULL);

-- consult_record: SLA 演示 (2 条已人工回复 reply_time <60 秒 + 1 条 is_auto=0 待处理)
INSERT INTO consult_record (student_id,question,answer,is_auto,create_time,reply_time) VALUES
(4,'如何重考不及格的考试?',
   '登录后进入"考试中心"，找到不及格考试，若 max_retry 未用尽可点"重考"按钮。剩余重考次数以考试设置为准。',
   2,'2026-07-09 09:00:00','2026-07-09 09:00:45'),
(6,'离线课件下载后怎么观看?',
   '进入"课程详情"页，点击"下载"保存 ZIP 包（仅 offline_filename 非空的课程支持），离线状态下仍可进入课程播放页',
   2,'2026-07-09 10:30:00','2026-07-09 10:30:38'),
(8,'少数民族语言课程什么时候上线?',
   '目前平台已完成多语言接口预留，具体语言包上线以省卫健委公告为准。已转产品开发团队跟进。',
   0,'2026-07-09 14:00:00',NULL);

-- ============================================================
-- 6. 校验 query - 直接输出关键 checksum (升級成功?)
-- ============================================================
SELECT '===== upgrade v1.2.0 -> v1.2.1 校验 =====' AS '=== checksum ===';
SELECT COUNT(*) AS roleperm_total FROM sys_role_permission;
SELECT role_id, COUNT(*) AS cnt FROM sys_role_permission GROUP BY role_id;
SELECT COUNT(*) AS student02_06_enroll FROM course_enroll WHERE student_id IN (5,6,7,8,9);
SELECT COUNT(*) AS study_records FROM study_record;
SELECT COUNT(*) AS consult_records FROM consult_record;
SELECT COUNT(*) AS pending_consult FROM consult_record WHERE is_auto = 0;
SELECT COUNT(*) AS in_progress_exam FROM exam_record WHERE status = 0;
SELECT id, title FROM question WHERE id = 9;
SELECT COUNT(*) AS offline_filename_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='training' AND TABLE_NAME='course' AND COLUMN_NAME='offline_filename';
