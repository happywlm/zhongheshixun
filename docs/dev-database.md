# 数据库设计文档（dev-database.md）

> 四川省基层卫生人员网络培训平台 — 数据库设计与实现手册
> 技术栈：Spring Boot + MySQL 8.0，共 **18 张表**（v1.3.0 新增 consult_keyword）
> 配套文档：[`开发文档.md`](./开发文档.md) | [`dev-backend.md`](./dev-backend.md) | [`dev-api.md`](./dev-api.md)

---

## 目录

- [第一部分：表清单概览](#第一部分表清单概览)
- [第二部分：每张表详细结构](#第二部分每张表详细结构)
- [第三部分：ER 关系图](#第三部分er-关系图)
- [第四部分：关键业务约束说明](#第四部分关键业务约束说明)
- [第五部分：分区策略补充](#第五部分分区策略补充)

---

## 第一部分：表清单概览

| 序号 | 表名 | 说明 | 初始数据行数 | 关键关联 |
|------|------|------|--------------|----------|
| 1 | `sys_user` | 用户表（学员/讲师/管理员） | 示例 3 条 | — |
| 2 | `teacher` | 培训讲师表 | 示例 2 条 | `user_id → sys_user.id` |
| 3 | `course` | 课程表 | 示例 2 条 | `teacher_id → teacher.id` |
| 4 | `course_chapter` | 课程章节表 | 示例 4 条 | `course_id → course.id` |
| 5 | `course_enroll` | 课程报名表 | 示例 2 条 | `student_id → sys_user.id`, `course_id → course.id` |
| 6 | `study_record` | 学习记录表（断点续播） | 示例 2 条 | 关联 `sys_user / course / course_chapter` |
| 7 | `knowledge_point` | 知识点表 | 示例 3 条 | `course_id → course.id` |
| 8 | `question` | 试题表（v1.3.0 新增 analysis） | 示例 3 条 | `course_id → course.id`, `knowledge_point_id → knowledge_point.id` |
| 9 | `exam` | 考试表 | 示例 2 条 | `course_id → course.id`, `plan_id → train_plan.id` |
| 10 | `exam_paper` | 试卷表（学员抽题快照） | 示例 1 条 | `exam_id → exam.id`, `student_id → sys_user.id` |
| 11 | `exam_record` | 考试记录表 | 示例 1 条 | `student_id / exam_id / paper_id` |
| 12 | `exam_answer` | 答题记录表 | 示例 2 条 | `record_id → exam_record.id`, `question_id → question.id` |
| 13 | `train_plan` | 培训计划表 | 示例 1 条 | — |
| 14 | `plan_course` | 计划关联课程表 | 示例 2 条 | `plan_id → train_plan.id`, `course_id → course.id` |
| 15 | `knowledge_base` | 知识库表（智能咨询） | 示例 2 条 | — |
| 16 | `consult_record` | 咨询记录表（含 SLA） | 示例 1 条 | `student_id → sys_user.id` |
| 17 | `consult_keyword` | **咨询关键词路由配置（v1.3.0 新增）** | 预置 6 条 | — |
| 18 | `resource_file` | 资源文件表 | 示例 2 条 | `course_id → course.id`, `uploader_id → sys_user.id` |

---

## 第二部分：每张表详细结构

### 1. sys_user — 用户表

**说明**：平台统一用户表，覆盖管理员、讲师、学员三类角色，通过 `role` 字段区分。密码使用 bcrypt 加密存储。

```sql
CREATE TABLE `sys_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '用户ID',
  `username`    VARCHAR(64)  NOT NULL                       COMMENT '登录用户名',
  `password`    VARCHAR(100) NOT NULL                       COMMENT '登录密码(bcrypt)',
  `name`        VARCHAR(64)  NOT NULL                       COMMENT '姓名',
  `phone`       VARCHAR(20)  DEFAULT NULL                   COMMENT '手机号',
  `email`       VARCHAR(128) DEFAULT NULL                   COMMENT '邮箱',
  `role`        VARCHAR(20)  NOT NULL DEFAULT 'student'     COMMENT '角色: admin/teacher/student',
  `avatar`      VARCHAR(255) DEFAULT NULL                   COMMENT '头像URL',
  `org_name`    VARCHAR(128) DEFAULT NULL                   COMMENT '所属机构',
  `job_type`    VARCHAR(20)  DEFAULT NULL                   COMMENT '岗位类型: 临床/公卫/护理/医技',
  `status`      TINYINT      NOT NULL DEFAULT 1              COMMENT '状态: 0禁用 1启用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 用户主键 |
| username | VARCHAR(64) | UNIQUE, NOT NULL | 登录账号 |
| password | VARCHAR(100) | NOT NULL | bcrypt 哈希，禁止明文 |
| name | VARCHAR(64) | NOT NULL | 真实姓名 |
| phone | VARCHAR(20) | — | 手机号，登录/找回用 |
| email | VARCHAR(128) | — | 邮箱 |
| role | VARCHAR(20) | NOT NULL, default student | admin/teacher/student |
| avatar | VARCHAR(255) | — | 头像资源地址 |
| org_name | VARCHAR(128) | — | 所属机构（乡镇卫生院等） |
| job_type | VARCHAR(20) | — | 临床/公卫/护理/医技 |
| status | TINYINT | NOT NULL, default 1 | 0禁用/1启用 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 自动维护更新时间 |

**索引说明**：主键 `id`；唯一键 `uk_username` 保证账号唯一。

---

### 2. teacher — 培训讲师表

**说明**：讲师档案，与 `sys_user` 一对一关联（通过 `user_id`），存储教学相关属性。

```sql
CREATE TABLE `teacher` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '讲师ID',
  `user_id`     BIGINT       NOT NULL                       COMMENT '关联用户ID',
  `name`        VARCHAR(64)  NOT NULL                       COMMENT '姓名',
  `title`        VARCHAR(64)  DEFAULT NULL                   COMMENT '职称',
  `education`    VARCHAR(32)  DEFAULT NULL                   COMMENT '学历',
  `direction`   VARCHAR(128) DEFAULT NULL                   COMMENT '教学方向',
  `intro`       TEXT         DEFAULT NULL                   COMMENT '讲师简介',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  CONSTRAINT `fk_teacher_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训讲师表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 讲师主键 |
| user_id | BIGINT | UNIQUE, FK → sys_user.id | 一对一关联用户 |
| name | VARCHAR(64) | NOT NULL | 姓名 |
| title | VARCHAR(64) | — | 职称（主任医师/副主任医师等） |
| education | VARCHAR(32) | — | 学历 |
| direction | VARCHAR(128) | — | 教学方向 |
| intro | TEXT | — | 个人简介 |
| create_time | DATETIME | NOT NULL | 创建时间 |

---

### 3. course — 课程表

**说明**：课程核心表，支持公开课/必修课两种类型，**v2.0 新增离线学习支持字段**。

```sql
CREATE TABLE `course` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT        COMMENT '课程ID',
  `title`        VARCHAR(128)  NOT NULL                       COMMENT '课程标题',
  `description`  TEXT          DEFAULT NULL                   COMMENT '课程描述',
  `cover_url`    VARCHAR(255)  DEFAULT NULL                   COMMENT '封面图URL',
  `teacher_id`    BIGINT        NOT NULL                       COMMENT '讲师ID',
  `course_type`  TINYINT       NOT NULL DEFAULT 1              COMMENT '课程类型: 1公开课 2必修课',
  `total_hours`  DECIMAL(6,2)  DEFAULT 0                      COMMENT '总学时',
  `offline_flag` TINYINT       NOT NULL DEFAULT 0              COMMENT '**v2.0新增** 是否可离线: 0否 1是',
  `zip_url`      VARCHAR(255)  DEFAULT NULL                   COMMENT '**v2.0新增** 离线ZIP下载路径',
  `status`       TINYINT       NOT NULL DEFAULT 0              COMMENT '状态: 0草稿 1已发布 2已下架',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_teacher` (`teacher_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_course_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 课程主键 |
| title | VARCHAR(128) | NOT NULL | 课程标题 |
| description | TEXT | — | 课程描述 |
| cover_url | VARCHAR(255) | — | 封面图 |
| teacher_id | BIGINT | FK → teacher.id | 主讲讲师 |
| course_type | TINYINT | NOT NULL, default 1 | 1公开课/2必修课 |
| total_hours | DECIMAL(6,2) | default 0 | 总学时 |
| **offline_flag** | TINYINT | NOT NULL, default 0 | **v2.0 新增** 是否可离线 0/1 |
| **zip_url** | VARCHAR(255) | — | **v2.0 新增** 离线 ZIP 包地址 |
| status | TINYINT | NOT NULL, default 0 | 0草稿/1已发布/2已下架 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

**索引说明**：`idx_teacher` 按讲师查询；`idx_status` 按状态筛选。

---

### 4. course_chapter — 课程章节表

**说明**：课程的章节/课时结构，每个章节对应一个视频资源。

```sql
CREATE TABLE `course_chapter` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '章节ID',
  `course_id`   BIGINT       NOT NULL                       COMMENT '课程ID',
  `title`       VARCHAR(128) NOT NULL                       COMMENT '章节标题',
  `sort_order`  INT          NOT NULL DEFAULT 0              COMMENT '排序序号',
  `video_url`   VARCHAR(255) DEFAULT NULL                   COMMENT '视频URL',
  `duration`    INT          DEFAULT 0                       COMMENT '视频时长(秒)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_sort` (`course_id`, `sort_order`),
  CONSTRAINT `fk_chapter_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程章节表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 章节主键 |
| course_id | BIGINT | FK → course.id | 所属课程 |
| title | VARCHAR(128) | NOT NULL | 章节标题 |
| sort_order | INT | NOT NULL, default 0 | 章节顺序 |
| video_url | VARCHAR(255) | — | 视频资源地址 |
| duration | INT | default 0 | 视频时长（秒） |
| create_time | DATETIME | NOT NULL | 创建时间 |

**索引说明**：联合索引 `idx_course_sort` 支持按课程+顺序查询章节列表。

---

### 5. course_enroll — 课程报名表

**说明**：学员与课程的多对多报名关系，**唯一约束保证同一学员同一课程只能报名一次**。

```sql
CREATE TABLE `course_enroll` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT        COMMENT '报名ID',
  `student_id`  BIGINT   NOT NULL                       COMMENT '学员ID',
  `course_id`   BIGINT   NOT NULL                       COMMENT '课程ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_course` (`student_id`, `course_id`),
  KEY `idx_course` (`course_id`),
  CONSTRAINT `fk_enroll_student` FOREIGN KEY (`student_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_enroll_course`  FOREIGN KEY (`course_id`)  REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程报名表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 报名主键 |
| student_id | BIGINT | FK → sys_user.id | 学员 |
| course_id | BIGINT | FK → course.id | 课程 |
| create_time | DATETIME | NOT NULL | 报名时间 |

**关键约束**：`uk_student_course` 唯一键防止重复报名。

---

### 6. study_record — 学习记录表

**说明**：记录学员每个章节的学习进度，支持**断点续播**（`last_position`）。**唯一约束保证每个学员每个章节仅一条记录**。

```sql
CREATE TABLE `study_record` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT        COMMENT '记录ID',
  `student_id`      BIGINT   NOT NULL                       COMMENT '学员ID',
  `course_id`       BIGINT   NOT NULL                       COMMENT '课程ID',
  `chapter_id`      BIGINT   NOT NULL                       COMMENT '章节ID',
  `progress`        INT      DEFAULT 0                       COMMENT '学习进度百分比(0-100)',
  `study_duration`  INT      DEFAULT 0                       COMMENT '累计学习时长(秒)',
  `last_position`   INT      DEFAULT 0                       COMMENT '断点续播位置(秒)',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study` (`student_id`, `course_id`, `chapter_id`),
  KEY `idx_student_course` (`student_id`, `course_id`),
  CONSTRAINT `fk_study_student` FOREIGN KEY (`student_id`)  REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_study_course`  FOREIGN KEY (`course_id`)   REFERENCES `course` (`id`),
  CONSTRAINT `fk_study_chapter` FOREIGN KEY (`chapter_id`)  REFERENCES `course_chapter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 记录主键 |
| student_id | BIGINT | FK → sys_user.id | 学员 |
| course_id | BIGINT | FK → course.id | 课程 |
| chapter_id | BIGINT | FK → course_chapter.id | 章节 |
| progress | INT | default 0 | 进度 0-100 |
| study_duration | INT | default 0 | 累计学习秒数 |
| last_position | INT | default 0 | 断点续播秒数 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

**关键约束**：`uk_study` 唯一键保证每个学员每个章节一条记录，重复学习走 UPDATE。

---

### 7. knowledge_point — 知识点表

**说明**：课程下的知识点条目，用于试题关联与学习重点标注。

```sql
CREATE TABLE `knowledge_point` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '知识点ID',
  `course_id`   BIGINT       NOT NULL                       COMMENT '课程ID',
  `name`        VARCHAR(128) NOT NULL                       COMMENT '知识点名称',
  `description` TEXT         DEFAULT NULL                   COMMENT '知识点描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_course` (`course_id`),
  CONSTRAINT `kp_fk_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 知识点主键 |
| course_id | BIGINT | FK → course.id | 所属课程 |
| name | VARCHAR(128) | NOT NULL | 知识点名称 |
| description | TEXT | — | 描述 |
| create_time | DATETIME | NOT NULL | 创建时间 |

---

### 8. question — 试题表

**说明**：题库，支持 5 种题型（单选/多选/判断/填空/问答），`options` 以 JSON 存储选项。`analysis` 列（v1.3.0 新增）存储答案解析，供编辑/详情页展示。

```sql
CREATE TABLE `question` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '试题ID',
  `course_id`           BIGINT       NOT NULL                       COMMENT '课程ID',
  `knowledge_point_id`  BIGINT       DEFAULT NULL                   COMMENT '知识点ID',
  `title`               VARCHAR(500) NOT NULL                       COMMENT '题目',
  `question_type`       TINYINT      NOT NULL DEFAULT 1              COMMENT '题型: 1单选 2多选 3判断 4填空 5问答',
  `options`             JSON         DEFAULT NULL                   COMMENT '选项(JSON)',
  `answer`              TEXT         DEFAULT NULL                   COMMENT '正确答案',
  `score`               DECIMAL(5,2) DEFAULT 0                      COMMENT '分值',
  `difficulty`          TINYINT      NOT NULL DEFAULT 2              COMMENT '难度: 1简单 2普通 3困难',
  `analysis`            TEXT         DEFAULT NULL                   COMMENT '答案解析(可选,编辑/详情页展示)',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_course` (`course_id`),
  KEY `idx_kp` (`knowledge_point_id`),
  CONSTRAINT `q_fk_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试题表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 试题主键 |
| course_id | BIGINT | FK → course.id | 所属课程 |
| knowledge_point_id | BIGINT | FK → knowledge_point.id | 关联知识点 |
| title | VARCHAR(500) | NOT NULL | 题干 |
| question_type | TINYINT | NOT NULL, default 1 | 1单选/2多选/3判断/4填空/5问答 |
| options | JSON | — | 选项 JSON，判断题可空 |
| answer | TEXT | — | 标准答案 |
| score | DECIMAL(5,2) | default 0 | 分值 |
| difficulty | TINYINT | NOT NULL, default 2 | 1简单/2普通/3困难 |
| analysis | TEXT | default NULL | 答案解析（v1.3.0 新增，可选，编辑/详情页展示） |
| create_time | DATETIME | NOT NULL | 创建时间 |

---

### 9. exam — 考试表

**说明**：考试配置表，通过 `exam_type` 区分三种考试类型，`course_id` 与 `plan_id` 按类型填写。

```sql
CREATE TABLE `exam` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT        COMMENT '考试ID',
  `title`          VARCHAR(128)  NOT NULL                       COMMENT '考试标题',
  `exam_type`      TINYINT       NOT NULL DEFAULT 1              COMMENT '考试类型: 1课程考试 2计划考试 3单独考试',
  `course_id`      BIGINT        DEFAULT NULL                   COMMENT '课程ID(课程考试时填写)',
  `plan_id`        BIGINT        DEFAULT NULL                   COMMENT '培训计划ID(计划考试时填写)',
  `total_score`     DECIMAL(6,2)  DEFAULT 100                    COMMENT '总分',
  `pass_score`     DECIMAL(6,2)  DEFAULT 60                     COMMENT '及格分',
  `duration`       INT           NOT NULL DEFAULT 60              COMMENT '考试时长(分钟)',
  `max_retry`      INT           NOT NULL DEFAULT 1              COMMENT '最大重考次数',
  `question_count` INT           NOT NULL DEFAULT 0              COMMENT '题目数量',
  `status`         TINYINT       NOT NULL DEFAULT 0              COMMENT '状态: 0草稿 1已发布',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_course` (`course_id`),
  KEY `idx_plan` (`plan_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `exam_fk_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `exam_fk_plan`   FOREIGN KEY (`plan_id`)   REFERENCES `train_plan` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 考试主键 |
| title | VARCHAR(128) | NOT NULL | 考试标题 |
| exam_type | TINYINT | NOT NULL, default 1 | 1课程考试/2计划考试/3单独考试 |
| course_id | BIGINT | FK → course.id, 可空 | 课程考试时必填 |
| plan_id | BIGINT | FK → train_plan.id, 可空 | 计划考试时必填 |
| total_score | DECIMAL(6,2) | default 100 | 总分 |
| pass_score | DECIMAL(6,2) | default 60 | 及格分 |
| duration | INT | NOT NULL, default 60 | 考试时长（分钟） |
| max_retry | INT | NOT NULL, default 1 | 最大重考次数 |
| question_count | INT | NOT NULL, default 0 | 题目数量 |
| status | TINYINT | NOT NULL, default 0 | 0草稿/1已发布 |
| create_time | DATETIME | NOT NULL | 创建时间 |

---

### 10. exam_paper — 试卷表

**说明**：每位学员每次考试生成一份试卷，`questions` 字段以 JSON 数组保存抽题快照，**保证同一学员同一考试只有一份试卷**。

```sql
CREATE TABLE `exam_paper` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT        COMMENT '试卷ID',
  `exam_id`     BIGINT   NOT NULL                       COMMENT '考试ID',
  `student_id`  BIGINT   NOT NULL                       COMMENT '学员ID',
  `questions`   JSON     DEFAULT NULL                   COMMENT '题目ID数组(JSON)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
  KEY `idx_student` (`student_id`),
  CONSTRAINT `paper_fk_exam`     FOREIGN KEY (`exam_id`)     REFERENCES `exam` (`id`),
  CONSTRAINT `paper_fk_student` FOREIGN KEY (`student_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 试卷主键 |
| exam_id | BIGINT | FK → exam.id | 所属考试 |
| student_id | BIGINT | FK → sys_user.id | 学员 |
| questions | JSON | — | 抽题 ID 数组快照 |
| create_time | DATETIME | NOT NULL | 创建时间 |

**关键约束**：`uk_exam_student` 唯一键保证一份考试一份试卷。

---

### 11. exam_record — 考试记录表

**说明**：学员的每一次考试作答记录，状态从"进行中→已提交→已批阅"流转。

```sql
CREATE TABLE `exam_record` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT        COMMENT '记录ID',
  `student_id`  BIGINT        NOT NULL                       COMMENT '学员ID',
  `exam_id`     BIGINT        NOT NULL                       COMMENT '考试ID',
  `paper_id`    BIGINT        NOT NULL                       COMMENT '试卷ID',
  `score`       DECIMAL(6,2)  DEFAULT NULL                   COMMENT '得分',
  `status`      TINYINT       NOT NULL DEFAULT 0              COMMENT '状态: 0进行中 1已提交 2已批阅',
  `start_time`  DATETIME      DEFAULT NULL                   COMMENT '开始时间',
  `submit_time` DATETIME      DEFAULT NULL                   COMMENT '提交时间',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_exam` (`student_id`, `exam_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `record_fk_student` FOREIGN KEY (`student_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `record_fk_exam`    FOREIGN KEY (`exam_id`)    REFERENCES `exam` (`id`),
  CONSTRAINT `record_fk_paper`   FOREIGN KEY (`paper_id`)   REFERENCES `exam_paper` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试记录表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 记录主键 |
| student_id | BIGINT | FK → sys_user.id | 学员 |
| exam_id | BIGINT | FK → exam.id | 考试 |
| paper_id | BIGINT | FK → exam_paper.id | 试卷 |
| score | DECIMAL(6,2) | — | 得分（批阅后写入） |
| status | TINYINT | NOT NULL, default 0 | 0进行中/1已提交/2已批阅 |
| start_time | DATETIME | — | 开始答题时间 |
| submit_time | DATETIME | — | 提交时间 |
| create_time | DATETIME | NOT NULL | 创建时间 |

---

### 12. exam_answer — 答题记录表

**说明**：每道题的作答明细，支持自动判分（客观题）与人工批阅（主观题）。

```sql
CREATE TABLE `exam_answer` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '答题ID',
  `record_id`      BIGINT       NOT NULL                       COMMENT '考试记录ID',
  `question_id`    BIGINT       NOT NULL                       COMMENT '试题ID',
  `student_answer` TEXT         DEFAULT NULL                   COMMENT '学员答案',
  `is_correct`     TINYINT      DEFAULT NULL                   COMMENT '是否正确: 0否 1是',
  `score`          DECIMAL(5,2) DEFAULT NULL                   COMMENT '本题得分',
  PRIMARY KEY (`id`),
  KEY `idx_record` (`record_id`),
  KEY `idx_question` (`question_id`),
  CONSTRAINT `ans_fk_record`   FOREIGN KEY (`record_id`)   REFERENCES `exam_record` (`id`),
  CONSTRAINT `ans_fk_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 答题主键 |
| record_id | BIGINT | FK → exam_record.id | 所属考试记录 |
| question_id | BIGINT | FK → question.id | 试题 |
| student_answer | TEXT | — | 学员作答内容 |
| is_correct | TINYINT | — | 0错误/1正确（主观题可空） |
| score | DECIMAL(5,2) | — | 本题得分 |

---

### 13. train_plan — 培训计划表

**说明**：培训计划（如"2026年民族县基层培训计划"），包含多门课程。

```sql
CREATE TABLE `train_plan` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '计划ID',
  `title`       VARCHAR(128) NOT NULL                       COMMENT '计划标题',
  `description` TEXT         DEFAULT NULL                   COMMENT '计划描述',
  `status`      TINYINT      NOT NULL DEFAULT 0              COMMENT '状态: 0草稿 1已发布 2已结束',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训计划表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 计划主键 |
| title | VARCHAR(128) | NOT NULL | 计划标题 |
| description | TEXT | — | 计划描述 |
| status | TINYINT | NOT NULL, default 0 | 0草稿/1已发布/2已结束 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

---

### 14. plan_course — 计划关联课程表

**说明**：培训计划与课程的多对多关联，含顺序与必修标记。

```sql
CREATE TABLE `plan_course` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT        COMMENT 'ID',
  `plan_id`     BIGINT   NOT NULL                       COMMENT '培训计划ID',
  `course_id`   BIGINT   NOT NULL                       COMMENT '课程ID',
  `sort_order`  INT      NOT NULL DEFAULT 0              COMMENT '顺序',
  `is_required` TINYINT  NOT NULL DEFAULT 1              COMMENT '是否必修: 0选修 1必修',
  PRIMARY KEY (`id`),
  KEY `idx_plan_sort` (`plan_id`, `sort_order`),
  KEY `idx_course` (`course_id`),
  CONSTRAINT `pc_fk_plan`   FOREIGN KEY (`plan_id`)   REFERENCES `train_plan` (`id`),
  CONSTRAINT `pc_fk_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划关联课程表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 主键 |
| plan_id | BIGINT | FK → train_plan.id | 培训计划 |
| course_id | BIGINT | FK → course.id | 课程 |
| sort_order | INT | NOT NULL, default 0 | 学习顺序 |
| is_required | TINYINT | NOT NULL, default 1 | 0选修/1必修 |

---

### 15. knowledge_base — 知识库表

**说明**：智能咨询知识库，支持关键词匹配与分类检索。

```sql
CREATE TABLE `knowledge_base` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '知识ID',
  `question`    VARCHAR(500) NOT NULL                       COMMENT '问题',
  `answer`      TEXT         NOT NULL                       COMMENT '答案',
  `keywords`    VARCHAR(500) DEFAULT NULL                   COMMENT '逗号分隔关键词',
  `category`    VARCHAR(64)  DEFAULT NULL                   COMMENT '分类',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  FULLTEXT KEY `ft_question` (`question`, `keywords`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 知识主键 |
| question | VARCHAR(500) | NOT NULL | 问题 |
| answer | TEXT | NOT NULL | 答案 |
| keywords | VARCHAR(500) | — | 逗号分隔关键词 |
| category | VARCHAR(64) | — | 分类 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

**索引说明**：`ft_question` 为 ngram 全文索引，支持中文关键词检索。

---

### 16. consult_record — 咨询记录表

**说明**：学员咨询记录，**v2.0 新增 SLA 计时字段**，支持智能问答与人工转接。

```sql
CREATE TABLE `consult_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '咨询ID',
  `student_id`     BIGINT       NOT NULL                       COMMENT '学员ID',
  `question`       VARCHAR(500) NOT NULL                       COMMENT '问题',
  `answer`         TEXT         DEFAULT NULL                   COMMENT '答案',
  `is_auto`        TINYINT      NOT NULL DEFAULT 1              COMMENT '回复方式: 1智能 2人工',
  `transfer_time`  DATETIME     DEFAULT NULL                   COMMENT '**v2.0新增** 转人工时间(SLA计时起点)',
  `reply_time`     DATETIME     DEFAULT NULL                   COMMENT '**v2.0新增** 人工回复时间',
  `sla_exceeded`   TINYINT      NOT NULL DEFAULT 0              COMMENT '**v2.0新增** 是否超时: 0否 1是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_student` (`student_id`),
  KEY `idx_sla` (`sla_exceeded`, `is_auto`),
  CONSTRAINT `consult_fk_student` FOREIGN KEY (`student_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询记录表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 咨询主键 |
| student_id | BIGINT | FK → sys_user.id | 学员 |
| question | VARCHAR(500) | NOT NULL | 问题 |
| answer | TEXT | — | 答案 |
| is_auto | TINYINT | NOT NULL, default 1 | 1智能/2人工 |
| **transfer_time** | DATETIME | — | **v2.0 新增** 转人工时间，SLA 起点 |
| **reply_time** | DATETIME | — | **v2.0 新增** 人工回复时间 |
| **sla_exceeded** | TINYINT | NOT NULL, default 0 | **v2.0 新增** 是否超时 0/1 |
| create_time | DATETIME | NOT NULL | 创建时间 |

**索引说明**：`idx_sla` 支持按超时状态+回复方式统计 SLA 达成率。

**v1.3.0 转人工流程**：学员点击"转人工客服"按钮 → `is_auto=0, answer=NULL, reply_time=NULL`（通过 LambdaUpdateWrapper 显式置空，规避 MyBatis-Plus 默认 NOT_NULL 策略）。命中关键词（见 consult_keyword 表）的提问将直接创建人工工单，跳过 AI 回复。

---

### 17. consult_keyword — 咨询关键词路由配置表（v1.3.0 新增）

**说明**：咨询关键词路由配置表（方案 A：AI 优先 + 关键词转人工）。学员提问命中 `to_human` 关键词时直接创建人工工单，跳过 AI 自动回复。预置 6 条转人工关键词（转人工/找老师/人工客服/真人/人工/客服），支持后台扩展。

```sql
CREATE TABLE `consult_keyword` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '关键词ID',
  `keyword`     VARCHAR(50)  NOT NULL                       COMMENT '关键词',
  `action`      VARCHAR(20)  NOT NULL DEFAULT 'to_human'     COMMENT '动作: to_human 转人工 / to_ai 转 AI',
  `sort_order`  INT          NOT NULL DEFAULT 0              COMMENT '排序(升序匹配)',
  `enabled`     TINYINT      NOT NULL DEFAULT 1              COMMENT '启用: 0 关闭 1 启用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0              COMMENT '逻辑删除: 0 正常 1 已删',
  PRIMARY KEY (`id`),
  KEY `idx_keyword` (`keyword`),
  KEY `idx_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询关键词路由配置表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 关键词主键 |
| keyword | VARCHAR(50) | NOT NULL | 关键词文本（命中即触发动作） |
| action | VARCHAR(20) | NOT NULL, default 'to_human' | to_human=转人工 / to_ai=转 AI |
| sort_order | INT | NOT NULL, default 0 | 排序（升序匹配，数字小优先） |
| enabled | TINYINT | NOT NULL, default 1 | 0 关闭 / 1 启用 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| deleted | TINYINT | NOT NULL, default 0 | 逻辑删除 |

**索引说明**：`idx_keyword` 支持快速查找关键词；`idx_enabled_sort` 支持按启用状态+排序高效取数。

**预置数据**（6 条）：

| keyword | action | sort_order | enabled |
|---------|--------|------------|---------|
| 转人工 | to_human | 1 | 1 |
| 找老师 | to_human | 2 | 1 |
| 人工客服 | to_human | 3 | 1 |
| 真人 | to_human | 4 | 1 |
| 人工 | to_human | 5 | 1 |
| 客服 | to_human | 6 | 1 |

**使用方式**：`ConsultServiceImpl.containsTransferHumanKeyword(question)` 查询 `enabled=1 AND action='to_human'` 的关键词，按 `sort_order` 升序遍历，若 `question.contains(keyword)` 返回 true，直接创建人工工单。

---

### 18. resource_file — 资源文件表

**说明**：课程关联的资源文件（视频/文档/PPT/PDF）。

```sql
CREATE TABLE `resource_file` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '资源ID',
  `course_id`    BIGINT       NOT NULL                       COMMENT '课程ID',
  `file_name`    VARCHAR(255) NOT NULL                       COMMENT '文件名',
  `file_url`     VARCHAR(255) NOT NULL                       COMMENT '文件URL',
  `file_type`    VARCHAR(20)  NOT NULL                       COMMENT '文件类型: video/document/ppt/pdf',
  `file_size`    BIGINT       DEFAULT 0                      COMMENT '文件大小(字节)',
  `uploader_id`  BIGINT       DEFAULT NULL                   COMMENT '上传者ID',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_course` (`course_id`),
  KEY `idx_type` (`file_type`),
  CONSTRAINT `res_fk_course`   FOREIGN KEY (`course_id`)   REFERENCES `course` (`id`),
  CONSTRAINT `res_fk_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源文件表';
```

**字段说明**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 资源主键 |
| course_id | BIGINT | FK → course.id | 所属课程 |
| file_name | VARCHAR(255) | NOT NULL | 文件名 |
| file_url | VARCHAR(255) | NOT NULL | 文件地址 |
| file_type | VARCHAR(20) | NOT NULL | video/document/ppt/pdf |
| file_size | BIGINT | default 0 | 文件大小（字节） |
| uploader_id | BIGINT | FK → sys_user.id | 上传者 |
| create_time | DATETIME | NOT NULL | 创建时间 |

---

## 第三部分：ER 关系图

> 文本示意图，展示关键外键关系（`1` 方表 → `N` 方表）。

```
sys_user (1) ──────── (N) teacher                (一对一, user_id)
sys_user (1) ──────── (N) course_enroll          (学员报名)
sys_user (1) ──────── (N) study_record           (学习记录)
sys_user (1) ──────── (N) exam_paper             (试卷)
sys_user (1) ──────── (N) exam_record            (考试记录)
sys_user (1) ──────── (N) consult_record         (咨询记录)
sys_user (1) ──────── (N) resource_file          (上传资源)

teacher (1) ──────── (N) course                  (讲师授课)

course (1) ──────── (N) course_chapter           (章节)
course (1) ──────── (N) course_enroll            (报名)
course (1) ──────── (N) study_record             (学习记录)
course (1) ──────── (N) knowledge_point          (知识点)
course (1) ──────── (N) question                 (试题)
course (1) ──────── (N) exam                     (课程考试)
course (1) ──────── (N) plan_course              (计划课程)
course (1) ──────── (N) resource_file            (资源)

course_chapter (1) ── (N) study_record           (章节学习记录)

knowledge_point (1) ─ (N) question               (知识点试题)

exam (1) ──────── (N) exam_paper               (考试试卷)
exam (1) ──────── (N) exam_record              (考试记录)

exam_paper (1) ──── (N) exam_record            (试卷记录)

exam_record (1) ─── (N) exam_answer            (答题明细)

train_plan (1) ──── (N) plan_course             (计划课程)
train_plan (1) ──── (N) exam                    (计划考试)
```

**关系说明**

- `sys_user` 为中心角色表，被多表外键引用。
- `course` 为核心业务表，章节/报名/学习/知识点/试题/考试/资源均围绕课程展开。
- 考试链路：`exam → exam_paper → exam_record → exam_answer`。
- 培训计划链路：`train_plan → plan_course → course`。

---

## 第四部分：关键业务约束说明

### 4.1 唯一约束

| 表 | 唯一键 | 业务含义 |
|----|--------|----------|
| `sys_user` | `uk_username` | 登录账号全局唯一 |
| `teacher` | `uk_user_id` | 一个用户只能对应一个讲师档案 |
| `course_enroll` | `uk_student_course` | **同一学员同一课程只能报名一次** |
| `study_record` | `uk_study(study` | **同一学员同一课程同一章节只有一条学习记录**（重复学习走 UPDATE） |
| `exam_paper` | `uk_exam_student` | **同一学员同一考试只能生成一份试卷** |

### 4.2 状态机

#### 课程状态（course.status）

```
0 草稿 ──发布──▶ 1 已发布 ──下架──▶ 2 已下架
```

- 草稿状态可编辑/删除；
- 已发布状态学员可见、可报名；
- 已下架状态学员不可见，已报名学员可继续学习。

#### 考试记录状态（exam_record.status）

```
0 进行中 ──提交──▶ 1 已提交 ──批阅──▶ 2 已批阅
```

- 进行中：学员答题中，计时未结束；
- 已提交：学员主动提交或自动交卷，等待批阅；
- 已批阅：客观题自动评分 + 主观题人工批阅完成，`score` 写入。

#### 培训计划状态（train_plan.status）

```
0 草稿 ──发布──▶ 1 已发布 ──结束──▶ 2 已结束
```

#### 考试配置状态（exam.status）

```
0 草稿 ──发布──▶ 1 已发布
```

### 4.3 考试类型区分（exam.exam_type）

| exam_type | 含义 | course_id | plan_id |
|-----------|------|-----------|---------|
| 1 | 课程考试 | **必填** | 空 |
| 2 | 计划考试 | 空 | **必填** |
| 3 | 单独考试 | 空 | 空 |

> 应用层需按 `exam_type` 校验 `course_id` / `plan_id` 的填写规则。

### 4.4 其他业务规则

- **密码安全**：`sys_user.password` 必须 bcrypt 哈希，禁止明文存储。
- **手机号/身份证脱敏**：查询接口需对敏感字段做脱敏处理。
- **学习进度**：`study_record.progress` 取值 0-100，由后端根据 `last_position / duration` 计算。
- **断点续播**：`last_position` 记录上次播放秒数，下次进入时从该位置起播。
- **SLA 计时**：`consult_record` 转人工后从 `transfer_time` 起算，超过阈值（如 30 分钟）置 `sla_exceeded = 1`。

---

## 第五部分：分区策略补充

### 5.1 分区策略说明

`study_record` 表随学习行为持续增长，建议按 `create_time` **RANGE 分区**（按月），便于：

- 快速清理历史数据（DROP PARTITION）；
- 按时间范围统计学习行为；
- 减少单表索引体积，提升查询性能。

### 5.2 study_record 按月分区 DDL（MySQL 8.0）

```sql
ALTER TABLE `study_record`
  PARTITION BY RANGE (TO_DAYS(`create_time`)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
    PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
    PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
    PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION pmax    VALUES LESS THAN MAXVALUE
  );
```

> **注意**：分区表需确保 `CREATE_TIME` 出现在主键/唯一键中（MySQL 要求）。如使用上述按月分区，需将 `create_time` 加入唯一键：
>
> ```sql
> -- 分区场景下的唯一键调整（create_time 必须参与唯一键）
> UNIQUE KEY `uk_study` (`student_id`, `course_id`, `chapter_id`, `create_time`)
> ```
>
> 业务层通过 `update_time` 维护"同一条记录"语义，避免跨月重复插入。

### 5.3 其他建议

- `exam_record`、`consult_record` 可按需同样采用 RANGE 分区（按 `create_time`）。
- 分区维护脚本建议通过定时任务每月新增下月分区，并归档超过 2 年的历史分区。

---

## 配套文档导航

| 文档 | 用途 |
|------|------|
| [`docs/设计文档.md`](./设计文档.md) | 单一事实源（架构 + 数据库 + API + 分工 + 计划） |
| [`docs/开发文档.md`](./开发文档.md) | 开发者实现手册（编码/联调/部署权威参考） |
| [`docs/dev-backend.md`](./dev-backend.md) | 后端开发手册（Spring Boot 实现细节） |
| [`docs/dev-api.md`](./dev-api.md) | API 接口文档 |
| [`docs/需求偏差说明.md`](./需求偏差说明.md) | 原始需求对照 + 简化理由 |
| [`docs/database.sql`](./database.sql) | 数据库初始化脚本（17 张表 + 示例数据） |

---

> **版本**：v1.0 | **维护人**：DB Engineer | **更新日期**：2026-07-07
