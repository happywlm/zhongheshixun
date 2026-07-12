-- ============================================================
-- 四川省基层卫生人员网络培训平台 - 数据库初始化脚本
-- 版本: v1.2.1 (2026-07-12)
--
-- 使用方法 (Git Bash / WSL 推荐, 切勿用 PowerShell 管线):
--   mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS training DEFAULT CHARSET utf8mb4;"
--   mysql -uroot -proot --default-character-set=utf8mb4 training < docs/database.sql
--
--   ⚠️ 切勿使用 PowerShell 管线 (Get-Content | mysql), 会把 UTF-8 字节破坏成 ASCII '?'
--   ⚠️ PowerShell 下请改用 Git Bash: bash -c "mysql ... < docs/database.sql"
--
-- 说明:
--   - 20 张表 + 20 条权限字典 + 47 条角色绑定 (RBAC v1.2.1)
--   - 演示账号(admin/teacher01/student01-06)密码统一: 123456
--   - 示例课程 4 门 / 知识点 5 条 / 试题 10 道 / 考试 3 场
--   - 备份了 1 场 student01 已批阅的考试记录(exam_record=1,exam_answer=10 行)
--   - 含 ECharts 演示素材: student02-06 报名/学习记录/咨询 SLA
--
-- v1.2.1 (2026-07-12) 变更 (合并 db-upgrade-v1_2_1.sql):
--   - [P0] roleperm: ADMIN 20 / TEACHER 17 / STUDENT 10 = 47 条 (原 35 条)
--   - [P1] 补齐 15 处高频查询索引 (course_chapter/course_enroll/study_record 等)
--   - [P2] question #9 去重 (高血压诊断标准 -> 高血压控制目标)
--   - [P2+] 演示数据均衡化 (course_enroll student02-06 / study_record 10 条 / consult_record 3 条)
--   - [P3] course.offline_filename 加列对齐设计文档
--
-- v1.2.0 (2026-07-12) 变更:
--   - teacher.user_id 允许 NULL（独立维护讲师档案场景,前端表单不强制传 user_id）
--   - question.question_type 允许 NULL + DEFAULT 1 (单选),避免新增试题时 500
--   - 已与 V2_1__fix_schema_align_entity.sql 同步,新部署时一次跑即可
--
-- 升级已有数据库:
--   - v1.2.0 -> v1.2.1: 跑 docs/db-upgrade-v1_2_1.sql
--   - 全新部署: 直接跑本文件 (一体化 20 张表 + v1.2.1 修复)
-- ============================================================

SET NAMES utf8mb4;USE training;
SET FOREIGN_KEY_CHECKS = 0;

-- 咨询记录表
DROP TABLE IF EXISTS consult_record;
-- 答卷记录表
DROP TABLE IF EXISTS exam_answer;
-- 试卷表
DROP TABLE IF EXISTS exam_paper;
-- 考试记录表
DROP TABLE IF EXISTS exam_record;
-- 考试表
DROP TABLE IF EXISTS exam;
-- 试题表
DROP TABLE IF EXISTS question;
-- 知识点表
DROP TABLE IF EXISTS knowledge_point;
-- 课程章节表
DROP TABLE IF EXISTS course_chapter;
-- 计划关联课程表
DROP TABLE IF EXISTS plan_course;
-- 培训计划表
DROP TABLE IF EXISTS train_plan;
-- 课程报名表
DROP TABLE IF EXISTS course_enroll;
-- 课程表
DROP TABLE IF EXISTS course;
-- 学习记录表
DROP TABLE IF EXISTS study_record;
-- 资源文件表
DROP TABLE IF EXISTS resource_file;
-- 知识库表
DROP TABLE IF EXISTS knowledge_base;
-- 培训讲师表
DROP TABLE IF EXISTS teacher;
-- 角色-权限关联表
DROP TABLE IF EXISTS sys_role_permission;
-- 权限字典表
DROP TABLE IF EXISTS sys_permission;
-- 角色字典表
DROP TABLE IF EXISTS sys_role;
-- 用户表
DROP TABLE IF EXISTS sys_user;

-- ============================================================
-- 表结构(20 张)
-- ============================================================

-- 用户表
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(50) NOT NULL COMMENT '用户名',
  password VARCHAR(100) NOT NULL COMMENT '密码(bcrypt加密)',
  real_name VARCHAR(50) COMMENT '姓名',
  phone VARCHAR(20) COMMENT '手机号',
  email VARCHAR(100) COMMENT '邮箱',
  role_id BIGINT DEFAULT NULL COMMENT '角色ID,FK->sys_role.id(RBAC 细粒度权限)',
  avatar VARCHAR(255) COMMENT '头像URL',
  org_name VARCHAR(200) COMMENT '所属机构(乡镇卫生院/社区卫生服务中心)',
  job_type VARCHAR(20) COMMENT '岗位类型:临床/公卫/护理/医技',
  status TINYINT DEFAULT 1 COMMENT '状态:0 禁用 1 启用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  UNIQUE KEY uk_username (username),
  KEY idx_user_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色字典表
CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
  role_code VARCHAR(30) NOT NULL COMMENT '角色编码:ADMIN/TEACHER/STUDENT',
  role_name VARCHAR(50) NOT NULL COMMENT '角色显示名',
  description VARCHAR(200) DEFAULT NULL COMMENT '角色说明',
  status TINYINT DEFAULT 1 COMMENT '状态:0 禁用 1 启用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色字典表';

-- 权限字典表
CREATE TABLE sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  perm_code VARCHAR(100) NOT NULL COMMENT '权限编码',
  perm_name VARCHAR(100) NOT NULL COMMENT '权限显示名',
  description VARCHAR(200) DEFAULT NULL COMMENT '权限说明',
  module VARCHAR(50) DEFAULT NULL COMMENT '归属模块',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限字典表';

-- 角色-权限关联表
CREATE TABLE sys_role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL COMMENT '角色ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_perm (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- 培训讲师表
CREATE TABLE teacher (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT DEFAULT NULL COMMENT '关联用户ID(可空,允许讲师独立维护档案)',
  real_name VARCHAR(50) NOT NULL COMMENT '讲师姓名',
  title VARCHAR(50) COMMENT '职称',
  education VARCHAR(50) COMMENT '学历',
  direction VARCHAR(200) COMMENT '教学方向',
  intro TEXT COMMENT '讲师简介',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  KEY idx_teacher_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训讲师表';

-- 课程表
CREATE TABLE course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '课程ID',
  title VARCHAR(200) NOT NULL COMMENT '课程名称',
  description TEXT COMMENT '课程描述',
  cover_url VARCHAR(255) DEFAULT NULL COMMENT '封面图 URL',
  teacher_id BIGINT DEFAULT NULL COMMENT '讲师ID',
  course_type TINYINT DEFAULT 1 COMMENT '类型:1 公开课 2 必修课',
  total_hours INT DEFAULT 0 COMMENT '总课时',
  status TINYINT DEFAULT 0 COMMENT '状态:0 草稿 1 已发布 2 已下架',
  offline_flag TINYINT DEFAULT 0 COMMENT '是否支持离线学习:0 否 1 是',
  offline_filename VARCHAR(255) DEFAULT NULL COMMENT '离线学习 ZIP 文件名(NULL 表示不可下载)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  KEY idx_course_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- 课程章节表
CREATE TABLE course_chapter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '章节ID',
  course_id BIGINT NOT NULL COMMENT '课程ID',
  title VARCHAR(200) NOT NULL COMMENT '章节标题',
  sort_order INT DEFAULT 0 COMMENT '排序',
  video_url VARCHAR(255) DEFAULT NULL COMMENT '视频地址',
  duration INT DEFAULT 0 COMMENT '时长(秒)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  KEY idx_chapter_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程章节表';

-- 课程报名表
CREATE TABLE course_enroll (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL COMMENT '学员ID',
  course_id BIGINT NOT NULL COMMENT '课程ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  UNIQUE KEY uk_student_course (student_id, course_id),
  KEY idx_enroll_student (student_id),
  KEY idx_enroll_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程报名表';

-- 学习记录表
CREATE TABLE study_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL COMMENT '学员ID',
  course_id BIGINT NOT NULL COMMENT '课程ID',
  chapter_id BIGINT NOT NULL COMMENT '章节ID',
  progress INT DEFAULT 0 COMMENT '进度百分比',
  study_duration INT DEFAULT 0 COMMENT '学习时长(秒)',
  last_position INT DEFAULT 0 COMMENT '上次播放位置(秒)',
  completed TINYINT DEFAULT 0 COMMENT '是否完成:0 否 1 是',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  UNIQUE KEY uk_record (student_id,course_id,chapter_id),
  KEY idx_study_student (student_id),
  KEY idx_study_chapter (chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

-- 知识点表
CREATE TABLE knowledge_point (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL COMMENT '课程ID',
  name VARCHAR(200) NOT NULL COMMENT '知识点名称',
  description TEXT COMMENT '知识点描述',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  KEY idx_kp_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';

-- 试题表
CREATE TABLE question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试题ID',
  course_id BIGINT DEFAULT NULL COMMENT '关联课程',
  knowledge_point_id BIGINT DEFAULT NULL COMMENT '关联知识点',
  title VARCHAR(500) NOT NULL COMMENT '题目',
  question_type TINYINT DEFAULT 1 COMMENT '类型:1 单选 2 多选 3 判断 4 填空 5 问答',
  options TEXT COMMENT '选项(JSON)',
  answer VARCHAR(500) NOT NULL COMMENT '正确答案',
  score INT DEFAULT 1 COMMENT '分值',
  difficulty TINYINT DEFAULT 2 COMMENT '难度:1 简单 2 中等 3 困难',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试题表';

-- 考试表
CREATE TABLE exam (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '考试ID',
  title VARCHAR(200) NOT NULL COMMENT '考试名称',
  exam_type TINYINT NOT NULL COMMENT '考试类型:1 课程考试 2 计划考试 3 单独考试',
  course_id BIGINT DEFAULT NULL COMMENT '课程考试关联课程ID',
  plan_id BIGINT DEFAULT NULL COMMENT '计划考试关联计划ID',
  total_score INT DEFAULT 100 COMMENT '总分',
  pass_score INT DEFAULT 60 COMMENT '及格分',
  duration INT DEFAULT 120 COMMENT '考试时长(分钟)',
  max_retry INT DEFAULT 1 COMMENT '最大重考次数',
  question_count INT DEFAULT 20 COMMENT '题目数量',
  question_ids VARCHAR(1000) DEFAULT NULL COMMENT '预组卷题目ID列表(JSON数组,按courseId关联优先抽题,题库不足时全库补足)',
  status TINYINT DEFAULT 0 COMMENT '状态:0 草稿 1 已发布',
  start_time DATETIME DEFAULT NULL COMMENT '考试开始时间',
  end_time DATETIME DEFAULT NULL COMMENT '考试结束时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试表';

-- 试卷表
CREATE TABLE exam_paper (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试卷ID',
  exam_id BIGINT NOT NULL COMMENT '考试ID',
  student_id BIGINT NOT NULL COMMENT '学员ID',
  questions TEXT NOT NULL COMMENT '题目列表(JSON 格式 question_id 数组)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  UNIQUE KEY uk_exam_student (exam_id, student_id),
  KEY idx_paper_exam (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷表';

-- 考试记录表
CREATE TABLE exam_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL COMMENT '学员ID',
  exam_id BIGINT NOT NULL COMMENT '考试ID',
  paper_id BIGINT DEFAULT NULL COMMENT '试卷ID',
  score INT DEFAULT 0 COMMENT '得分',
  status TINYINT DEFAULT 0 COMMENT '状态:0 进行中 1 已提交 2 已批阅',
  start_time DATETIME DEFAULT NULL COMMENT '开始时间',
  submit_time DATETIME DEFAULT NULL COMMENT '提交时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试记录表';

-- 答卷记录表
CREATE TABLE exam_answer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_id BIGINT NOT NULL COMMENT '考试记录ID',
  question_id BIGINT NOT NULL COMMENT '试题ID',
  student_answer TEXT COMMENT '学员答案',
  is_correct TINYINT DEFAULT NULL COMMENT '是否正确:0 错 1 对',
  score INT DEFAULT 0 COMMENT '得分',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  KEY idx_answer_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答卷记录表';

-- 培训计划表
CREATE TABLE train_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '计划ID',
  title VARCHAR(200) NOT NULL COMMENT '计划名称',
  description TEXT COMMENT '计划描述',
  status TINYINT DEFAULT 0 COMMENT '状态:0 草稿 1 已发布 2 已结束',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训计划表';

-- 计划关联课程表
CREATE TABLE plan_course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL COMMENT '计划ID',
  course_id BIGINT NOT NULL COMMENT '课程ID',
  sort_order INT DEFAULT 0 COMMENT '学习顺序',
  is_required TINYINT DEFAULT 1 COMMENT '是否必修:0 否 1 是',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  KEY idx_plan_course_plan (plan_id),
  KEY idx_plan_course_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划关联课程表';

-- 知识库表
CREATE TABLE knowledge_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question VARCHAR(500) NOT NULL COMMENT '问题',
  answer TEXT NOT NULL COMMENT '回答',
  keywords VARCHAR(200) DEFAULT NULL COMMENT '关键词(逗号分隔,用于匹配)',
  category VARCHAR(50) DEFAULT NULL COMMENT '分类',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 咨询记录表
CREATE TABLE consult_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL COMMENT '学员ID',
  question VARCHAR(500) NOT NULL COMMENT '问题',
  answer TEXT COMMENT '回答',
  is_auto TINYINT DEFAULT 1 COMMENT '类型:1 智能回答 2 人工回答 0 待处理',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  reply_time DATETIME COMMENT '回复时间(用于 SLA 统计)',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  KEY idx_consult_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询记录表';

-- 资源文件表
CREATE TABLE resource_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT COMMENT '课程ID',
  file_name VARCHAR(200) NOT NULL COMMENT '文件名',
  file_url VARCHAR(255) NOT NULL COMMENT '文件URL',
  file_type VARCHAR(50) COMMENT '文件类型:video/document/ppt/pdf',
  file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
  uploader_id BIGINT COMMENT '上传者ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0 正常 1 已删',
  KEY idx_resource_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源文件表';


-- ============================================================
-- 初始化数据
-- ============================================================

-- 用户:admin + teacher×2 + student×6 (密码统一 123456)
INSERT INTO sys_user (id,username,password,real_name,phone,role_id,org_name,job_type,status,create_time,deleted) VALUES
(1,'admin','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','系统管理员','13800000000',1,'四川省卫健委',NULL,1,'2026-07-09 00:00:00',0),
(2,'teacher01','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','张教授','13800000001',2,'四川省人民医院',NULL,1,'2026-07-09 00:00:00',0),
(3,'teacher02','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','李主任','13800000002',2,'成都医学院',NULL,1,'2026-07-09 00:00:00',0),
(4,'student01','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','王医生','13800000003',3,'汶川县人民医院','临床',1,'2026-07-09 00:00:00',0),
(5,'student02','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','赵护士','13800000004',3,'茂县中医院','护理',1,'2026-07-09 00:00:00',0),
(6,'student03','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','刘医生','13800000005',3,'理县人民医院','临床',1,'2026-07-09 00:00:00',0),
(7,'student04','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','陈医生','13800000006',3,'小金县人民医院','公卫',1,'2026-07-09 00:00:00',0),
(8,'student05','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','杨老师','13800000007',3,'黑水县人民医院','护理',1,'2026-07-09 00:00:00',0),
(9,'student06','$2a$10$EEeUC1lM2mbe.nOY0CtsDOVYQciytNhzUMLR2rAgI5nfOXzmlGJPK','黄医师','13800000008',3,'松潘县人民医院','医技',1,'2026-07-09 00:00:00',0);

-- 角色字典:ADMIN/TEACHER/STUDENT
INSERT INTO sys_role (id,role_code,role_name,description,status,create_time,deleted) VALUES
(1,'ADMIN',  '系统管理员','全部权限',          1,'2026-07-09 00:00:00',0),
(2,'TEACHER','培训讲师','课程/试题/考试/咨询',1,'2026-07-09 00:00:00',0),
(3,'STUDENT','学员',    '学习/考试/咨询',     1,'2026-07-09 00:00:00',0);

-- 权限字典(20 条:10 模块 × read/write)
INSERT INTO sys_permission (id,perm_code,perm_name,description,module,create_time,deleted) VALUES
(1,'course:read','课程查看','课程列表/详情可见','course','2026-07-09 00:00:00',0),
(2,'course:write','课程编辑','新增/修改/删除课程','course','2026-07-09 00:00:00',0),
(3,'chapter:read','章节查看','章节列表/详情可见','chapter','2026-07-09 00:00:00',0),
(4,'chapter:write','章节编辑','新增/修改/删除章节','chapter','2026-07-09 00:00:00',0),
(5,'knowledge:read','知识点查看','知识点列表/详情可见','knowledge','2026-07-09 00:00:00',0),
(6,'knowledge:write','知识点编辑','新增/修改/删除知识点','knowledge','2026-07-09 00:00:00',0),
(7,'question:read','题目查看','题库列表/详情可见','question','2026-07-09 00:00:00',0),
(8,'question:write','题目编辑','新增/修改/删除题目','question','2026-07-09 00:00:00',0),
(9,'exam:read','考试查看','考试列表/详情可见','exam','2026-07-09 00:00:00',0),
(10,'exam:write','考试编辑','新增/修改/删除考试/发布','exam','2026-07-09 00:00:00',0),
(11,'consult:read','咨询查看','咨询工单列表/处理页可见','consult','2026-07-09 00:00:00',0),
(12,'consult:write','咨询处理','接单/回复/闭环','consult','2026-07-09 00:00:00',0),
(13,'stats:read','统计查看','学习/考试/综合统计页可见','stats','2026-07-09 00:00:00',0),
(14,'user:read','用户查看','用户/角色/菜单管理页可见','user','2026-07-09 00:00:00',0),
(15,'user:write','用户编辑','用户/角色/菜单的增删改','user','2026-07-09 00:00:00',0),
(16,'plan:read','计划查看','培训计划列表/详情可见','plan','2026-07-09 00:00:00',0),
(17,'plan:write','计划编辑','培训计划增删改/关联课程','plan','2026-07-09 00:00:00',0),
(18,'teacher:read','讲师查看','讲师列表/详情可见','teacher','2026-07-09 00:00:00',0),
(19,'teacher:write','讲师编辑','新增/修改/删除讲师','teacher','2026-07-09 00:00:00',0),
(20,'resource:read','资源查看','资源列表/详情可见','resource','2026-07-09 00:00:00',0);

-- 角色-权限绑定 v1.2.1: ADMIN 20 / TEACHER 17 / STUDENT 10 = 47 条
-- TEACHER: 课程+章节+知识+题目+考试+咨询+计划+讲师+资源+统计 (无 user 管理)
-- STUDENT: 只读 + consult:write (提问) + resource (下载)
INSERT INTO sys_role_permission (role_id,permission_id,create_time) VALUES
-- ADMIN: 全 20 条
(1,1,'2026-07-09 00:00:00'),(1,2,'2026-07-09 00:00:00'),(1,3,'2026-07-09 00:00:00'),(1,4,'2026-07-09 00:00:00'),(1,5,'2026-07-09 00:00:00'),
(1,6,'2026-07-09 00:00:00'),(1,7,'2026-07-09 00:00:00'),(1,8,'2026-07-09 00:00:00'),(1,9,'2026-07-09 00:00:00'),(1,10,'2026-07-09 00:00:00'),
(1,11,'2026-07-09 00:00:00'),(1,12,'2026-07-09 00:00:00'),(1,13,'2026-07-09 00:00:00'),(1,14,'2026-07-09 00:00:00'),(1,15,'2026-07-09 00:00:00'),
(1,16,'2026-07-09 00:00:00'),(1,17,'2026-07-09 00:00:00'),(1,18,'2026-07-09 00:00:00'),(1,19,'2026-07-09 00:00:00'),(1,20,'2026-07-09 00:00:00'),
-- TEACHER: 17 条 (1-13 + 16,17,18,20)
(2,1,'2026-07-09 00:00:00'),(2,2,'2026-07-09 00:00:00'),(2,3,'2026-07-09 00:00:00'),(2,4,'2026-07-09 00:00:00'),(2,5,'2026-07-09 00:00:00'),
(2,6,'2026-07-09 00:00:00'),(2,7,'2026-07-09 00:00:00'),(2,8,'2026-07-09 00:00:00'),(2,9,'2026-07-09 00:00:00'),(2,10,'2026-07-09 00:00:00'),
(2,11,'2026-07-09 00:00:00'),(2,12,'2026-07-09 00:00:00'),(2,13,'2026-07-09 00:00:00'),
(2,16,'2026-07-09 00:00:00'),(2,17,'2026-07-09 00:00:00'),(2,18,'2026-07-09 00:00:00'),(2,20,'2026-07-09 00:00:00'),
-- STUDENT: 10 条 (1,3,5,7,9,11,12,13,16,20)
(3,1,'2026-07-09 00:00:00'),(3,3,'2026-07-09 00:00:00'),(3,5,'2026-07-09 00:00:00'),(3,7,'2026-07-09 00:00:00'),(3,9,'2026-07-09 00:00:00'),
(3,11,'2026-07-09 00:00:00'),(3,12,'2026-07-09 00:00:00'),(3,13,'2026-07-09 00:00:00'),(3,16,'2026-07-09 00:00:00'),(3,20,'2026-07-09 00:00:00');

-- 讲师数据
INSERT INTO teacher (id,user_id,real_name,title,education,direction,intro,create_time,deleted) VALUES
(1,2,'张教授','主任医师','博士','临床医学','从事临床医学教学20年,擅长常见病诊疗','2026-07-09 00:00:00',0),
(2,3,'李主任','副主任医师','硕士','公共卫生','公共卫生领域专家,主持多项省级课题','2026-07-09 00:00:00',0);

-- 课程数据(4 门)
INSERT INTO course (id,title,description,teacher_id,course_type,total_hours,status,offline_flag,create_time,deleted) VALUES
(1,'基层常见病诊疗规范','针对基层医生的常见病诊疗规范培训',1,2,40,1,0,'2026-07-09 00:00:00',0),
(2,'公共卫生服务实务','基本公共卫生服务实务培训',         2,2,30,1,0,'2026-07-09 00:00:00',0),
(3,'急救技能培训','基层急救技能培训',                      1,1,20,1,0,'2026-07-09 00:00:00',0),
(4,'护理基础操作','护理基础操作规范',                      2,1,25,2,0,'2026-07-09 00:00:00',0);

-- 章节数据(5 行)
INSERT INTO course_chapter (id,course_id,title,sort_order,video_url,duration,create_time,deleted) VALUES
(1,1,'第一章 常见病概述',             1,'/video/ch1-1.mp4',1800,'2026-07-09 00:00:00',0),
(2,1,'第二章 诊断要点',               2,'/video/ch1-2.mp4',2400,'2026-07-09 00:00:00',0),
(3,1,'第三章 治疗方案',               3,'/video/ch1-3.mp4',2000,'2026-07-09 00:00:00',0),
(4,2,'第一章 公共卫生服务概述',       1,'/video/ch2-1.mp4',1500,'2026-07-09 00:00:00',0),
(5,2,'第二章 慢病管理',               2,'/video/ch2-2.mp4',2100,'2026-07-09 00:00:00',0);

-- 知识点数据(5 行)
INSERT INTO knowledge_point (id,course_id,name,description,create_time,deleted) VALUES
(1,1,'高血压的诊断标准','高血压的诊断标准和分级',              '2026-07-09 00:00:00',0),
(2,1,'糖尿病的治疗原则','糖尿病的治疗原则和用药规范',          '2026-07-09 00:00:00',0),
(3,2,'健康档案管理',       '居民健康档案的建立和管理',                 '2026-07-09 00:00:00',0),
(4,3,'基层卫生服务规范','基层医疗卫生机构服务规范与流程',    '2026-07-09 00:00:00',0),
(5,3,'急救基本知识',    '常见急症的识别与初步处理原则',          '2026-07-09 00:00:00',0);

-- 试题数据(10 道)
INSERT INTO question (id,course_id,knowledge_point_id,title,question_type,options,answer,score,difficulty,create_time,deleted) VALUES
(1,1,1,'高血压的诊断标准是收缩压≥（）mmHg',1,'["A. 130","B. 140","C. 150","D. 160"]','B',2,1,'2026-07-09 00:00:00',0),
(2,1,1,'以下哪些属于高血压的危险因素',2,'["A. 高盐饮食","B. 肥胖","C. 遗传因素","D. 以上都是"]','D',3,2,'2026-07-09 00:00:00',0),
(3,1,2,'糖尿病患者应首选饮食治疗',3,'["正确","错误"]','正确',2,1,'2026-07-09 00:00:00',0),
(4,2,3,'健康档案的建立原则是（）',4,'','自愿与规范相结合',3,2,'2026-07-09 00:00:00',0),
(5,3,4,'基层卫生服务机构的首诊负责制是指（）',1,'["A. 谁首诊谁负责到底","B. 仅负责初步诊断","C. 不负责转诊","D. 以上都不对"]','A',2,1,'2026-07-09 00:00:00',0),
(6,3,4,'居民健康档案的规范服务内容包含哪些',2,'["A. 健康评估","B. 健康教育","C. 慢病管理","D. 以上全部"]','D',3,2,'2026-07-09 00:00:00',0),
(7,3,5,'心脏骤停的黄金抢救时间是 4 分钟内',3,'["正确","错误"]','正确',2,1,'2026-07-09 00:00:00',0),
(8,3,5,'成人心肺复苏的按压频率是（）次/分',4,'','100-120',3,2,'2026-07-09 00:00:00',0),
(9,1,1,'高血压患者血压控制目标一般为＜（）mmHg',1,'["A. 130/85","B. 140/90","C. 150/95","D. 160/100"]','B',2,1,'2026-07-09 00:00:00',0),
(10,1,1,'高血压的诊断标准是舒张压≥（）mmHg',1,'["A.80","B.90","C.100","D.110"]','B',2,1,'2026-07-09 00:00:00',0);

-- 考试数据(3 场,含预组卷 question_ids)
-- 抽题策略:按 course_id 关联优先抽题,题库不足时全库补足
-- 题库分布:courseId=1→5题(id 1,2,3,9,10);courseId=2→1题(id 4);courseId=3→4题(id 5,6,7,8)
INSERT INTO exam (id,title,exam_type,course_id,plan_id,total_score,pass_score,duration,max_retry,question_count,question_ids,status,start_time,end_time,create_time,deleted) VALUES
(1,'基层常见病诊疗规范期末考试',1,1,NULL,100,60,120,3,10,'[1,2,3,9,10,4,5,6,7,8]',1,'2026-01-01','2027-12-31','2026-07-09 00:00:00',0),
(2,'公共卫生服务实务期末考试',  1,2,NULL,100,60,120,3, 8,'[4,1,2,3,5,6,7,8]',1,'2026-01-01','2027-12-31','2026-07-09 00:00:00',0),
(3,'2026年度结业考试',          1,3,NULL,100,60,150,1,10,'[5,6,7,8,1,2,3,4,9,10]',1,'2026-01-01','2027-12-31','2026-07-09 00:00:00',0);

-- 培训计划数据(2 行)
INSERT INTO train_plan (id,title,description,status,create_time,deleted) VALUES
(1,'2026年民族县基层医生培训计划','针对67个民族县的基层医生培训',1,'2026-07-09 00:00:00',0),
(2,'2026年护理人员培训计划',    '护理人员专项培训',              0,'2026-07-09 00:00:00',0);

-- 计划课程关联(3 行)
INSERT INTO plan_course (id,plan_id,course_id,sort_order,is_required,create_time,deleted) VALUES
(1,1,1,1,1,'2026-07-09 00:00:00',0),
(2,1,2,2,1,'2026-07-09 00:00:00',0),
(3,1,3,3,0,'2026-07-09 00:00:00',0);

-- 知识库数据(5 条,智能问答)
INSERT INTO knowledge_base (id,question,answer,keywords,category,create_time,deleted) VALUES
(1,'如何报名课程?','在课程列表中选择课程,点击"报名"按钮即可报名。','报名,课程,如何报名','平台使用','2026-07-09 00:00:00',0),
(2,'考试不及格怎么办?','可以参加重考,具体重考次数以考试设置为准。','考试,不及格,重考','考试相关','2026-07-09 00:00:00',0),
(3,'学习进度如何查看?',"在'我的课程'中可以查看每门课程的学习进度。","学习进度,查看,我的课程",'学习相关','2026-07-09 00:00:00',0),
(4,'忘记密码怎么办?','请联系系统管理员重置密码。','密码,忘记,重置','账户相关','2026-07-09 00:00:00',0),
(5,'如何下载离线课件?','在课程详情页点击"下载"按钮即可。','下载,离线,ZIP','平台使用','2026-07-09 00:00:00',0);

-- 资源文件数据(3 行)
INSERT INTO resource_file (id,course_id,file_name,file_url,file_type,file_size,uploader_id,create_time,deleted) VALUES
(1,1,'第一章课件.pdf',           '/resource/ch1.pdf',  'pdf',     2048000,2,'2026-07-09 00:00:00',0),
(2,1,'第二章课件.pdf',           '/resource/ch2.pdf',  'pdf',     3072000,2,'2026-07-09 00:00:00',0),
(3,2,'公共卫生服务手册.docx',     '/resource/manual.docx','document',1536000,3,'2026-07-09 00:00:00',0);

-- student01 报名了 3 门课程
INSERT INTO course_enroll (id,student_id,course_id,create_time,deleted) VALUES
(1,4,1,'2026-07-09 00:00:00',0),
(2,4,2,'2026-07-09 00:00:00',0),
(3,4,3,'2026-07-09 00:00:00',0);

-- exam_record:student01 已批阅的 exam_id=1 记录(成绩 26 分/不及格)
INSERT INTO exam_record (id,student_id,exam_id,paper_id,score,status,start_time,submit_time,create_time,deleted) VALUES
(1,4,1,1,26,2,'2026-07-09 15:16:57','2026-07-09 15:17:10','2026-07-09 00:00:00',0);

-- exam_paper:student01 × exam_id=1
INSERT INTO exam_paper (id,exam_id,student_id,questions,create_time,deleted) VALUES
(1, 1, 4, '[1,2,3,4,5,6,7,8,9,10]', '2026-07-09 15:16:57', 0);

-- exam_answer:student01 × exam_id=1 的 10 道答题(正确 5 题/错误 5 题=26 分)
INSERT INTO exam_answer (id,record_id,question_id,student_answer,is_correct,score,create_time,deleted) VALUES
(1,1,1,'B',1,2,'2026-07-09 15:17:10',0),
(2,1,2,'D',1,3,'2026-07-09 15:17:10',0),
(3,1,3,'正确',1,2,'2026-07-09 15:17:10',0),
(4,1,4,'自愿与规范结合',1,3,'2026-07-09 15:17:10',0),
(5,1,5,'A',1,2,'2026-07-09 15:17:10',0),
(6,1,6,'B',0,0,'2026-07-09 15:17:10',0),
(7,1,7,'错误',0,0,'2026-07-09 15:17:10',0),
(8,1,8,'100-120',1,3,'2026-07-09 15:17:10',0),
(9,1,9,'A',0,0,'2026-07-09 15:17:10',0),
(10,1,10,'A',0,0,'2026-07-09 15:17:10',0);

-- ============================================================
-- v1.2.1 演示数据均衡化 (ECharts/联调素材)
-- ============================================================

-- course_enroll: student02-06 每人至少报名 1 门课程
INSERT INTO course_enroll (student_id,course_id) VALUES
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

-- exam_record: student02 进行中考试 (status=0 未提交 用于考试中心列表演示)
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

SET FOREIGN_KEY_CHECKS = 1;