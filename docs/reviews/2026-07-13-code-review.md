# 代码评审报告（2026-07-13）

**评审人**：Claude Code `/code-review`（tech-director 子代理执行）
**评审时间**：2026-07-13
**评审范围**：
- 后端：`training-parent/{training-common, training-dao, training-service, training-admin, training-api}/`
- 前端：`web-student/src/`（28 个文件，Vue 3 + TS + Vue Router + Pinia + Element Plus + Vite）

**总体评价**：⚠️ **WARN** — MVP 功能链路三端齐备、接口穿透通过，代码质量整体良好，但**安全 / 正确性**存在若干答辩必问隐患，需优先修复 Top 5 后再进入联调验收。

---

## 一、运行时端口拓扑（本报告采纳的实际值）

| 服务 | 实际端口 | 配置来源 |
|---|---|---|
| training-admin 后端 | **9898** | `training-admin/src/main/resources/application.yml` |
| training-api 后端（业务 + 小程序） | **9899** | `training-api/src/main/resources/application.yml` |
| web-student 前端（Vite dev） | **5174** | `web-student/vite.config.*` |
| Vite 代理 `/admin` → | 9898 | `web-student/vite.config.*` |
| Vite 代理 `/api` → | **9899** | `web-student/vite.config.*` |
| 管理后台前端 | 5176 | 独立 Vite 工程 |

> ⚠️ 本报告已纠正评审子代理关于「8080/8081」的描述：代码注释里的 8080/8081 是 2026-07-09 迁移前的**历史残留**，实际运行态已是 9898/9899。详见 §七「纠正项」。

---

## 二、严重问题（必须修复，答辩高频）

| # | 端 | 文件 | 行 | 问题 | 答辩风险 |
|---|---|---|---|---|---|
| 1 | 后端 | `training-admin/src/main/resources/application.yml` | 19-20, 62 | **DB 密码 `root/root` 明文 + JWT secret 弱密钥硬编码**（41 字节拼音串，语义化可猜测） | 高 — 伪造 JWT / 数据库裸奔 |
| 2 | 后端 | `training-service/.../ExamBizServiceImpl.java` | 368 | **submitExam 防重复提交仅靠 `status` 判断**，并发双写可双插 `exam_answer`、成绩被覆盖 | 高 — 考试防作弊必考 |
| 3 | 后端 | `training-api/.../WxAuthController.java` | 201 | **演示账号默认密码 `"123456"` 硬编码源码** | 高 — 答辩必问项 |
| 4 | 后端 | 多处业务接口 | — | **水平越权**：transfer-human 可清空他人 AI 回复；`exam/record/{id}` 可查看他人作答详情；管理员重置密码无等级限制可接管其他管理员 | 高 — 权限必考 |
| 5 | 后端 | `training-service/.../ExamBizServiceImpl.java` | 504 | **多选判分用 char 集合全或无比较**，无部分给分、复合 key 会错判 | 中 — 自动阅卷算法 |
| 6 | 后端 | `training-dao/.../StudyRecordMapper.xml` | 6 | **upsert 依赖唯一索引**，DDL 缺 UNIQUE KEY 则进度只增不改 | 中 — 学习进度 |
| 7 | 前端 | `web-student/src/views/exam/answer.vue` | 104 | **倒计时纯前端 `Date.now()` 计算**，用户可改系统时钟延长时间；`doSubmit` 不把开始/结束时间发给后端 | 高 — 考试防作弊必考 |
| 8 | 前端 | `web-student/src/views/exam/result.vue` | 114 | **成绩 / 是否通过走 URL query 明文传**，用户可改 `passed=false→true`、`score=100` | 高 — 成绩伪造 |
| 9 | 前端 | `web-student/src/views/exam/answer.vue` | 220 | **`onBeforeRouteLeave` 不清理计时器**，keep-alive / 路由复用时 setInterval 泄漏 | 中 — 答辩必查项 |

---

## 三、警告（建议修复）

| # | 端 | 文件 | 行 | 问题 | 建议 |
|---|---|---|---|---|---|
| 10 | 后端 | `training-service/.../ExamBizServiceImpl.java` | 445 | 判题临时调试 info 日志残留（注释明示"发布前删除"） | 删除 |
| 11 | 后端 | `JwtAuthenticationFilter.java` / `JwtUtils.java` | 106 / 97 | JWT 认证异常把 `e.getMessage()` 直接回写前端 | 统一返回"认证失败" |
| 12 | 后端 | `CourseController.java` | 33 | Controller 直接注入 Mapper 绕过 Service（TrainPlan / CourseApi 同） | 走 Service |
| 13 | 后端 | `CourseServiceImpl.java` | 50 | update 用 copyProperties 导致 null 字段误覆盖 | 用 `update(null, wrapper)` 或 `@TableField(updateStrategy=IGNORED)` |
| 14 | 后端 | `QuestionMapper.xml` / `ExamBizServiceImpl.java` | 40 / 628 | 大题库全量加载 / 全表 exam 内存分页 | 加 LIMIT / 分页 |
| 15 | 前端 | `web-student/src/views/login/index.vue` | 85 | validate 回调 + async 混用反模式，校验不可靠；无密码长度 / 用户名格式校验 | 改 async await + 加 rules |
| 16 | 前端 | `web-student/src/views/exam/answer.vue` | 144 | 考试加载 / 提交无 AbortController，快速切换竞态 | 加 AbortController |
| 17 | 前端 | `web-student/src/router/index.js` | 134 | 硬编码 `http://localhost:5176/` 管理后台地址 | 改 `import.meta.env.VITE_ADMIN_URL` |
| 18 | 前端 | `web-student/src/api/user.js` / `utils/request.js` | 4-12 / 5 | 注释残留历史端口 8080/8081 + "僵尸进程"过程文字 | 改成 9898/9899，删过程描述 |
| 19 | 前端 | `web-student/src/views/exam/result.vue` | 107 | `reviewList` 未使用变量；若后端返回题目详情会被静默丢弃（错题回看功能缺失） | 删变量或实现 UI |
| 20 | 前端 | `web-student/src/views/exam/list.vue` | 102, 106 | `exam.status` 双义 magic number；`canStart` 依赖未验证字段 `retryLeft` | 抽枚举常量 |
| 21 | 前端 | `web-student/src/views/home/index.vue` | 186 | 残留 `console.warn` 调试日志 | 删除 |
| 22 | 前端 | `web-student/src/views/course/list.vue` | 39 | 字符串模板拼接 background-image url 未校验 coverUrl | 加 URL 校验 |
| 23 | 前端 | `web-student/src/views/course/learn.vue` | 124 | 用 `route.params.id` 快照而非 computed，路由复用不刷新 | 改 computed / watch |
| 24 | 前端 | `web-student/src/views/course/detail.vue` / `learn.vue` | 246 / 206 | 重复实现 `checkEnrolled`（DRY 违反） | 抽 composable |
| 25 | 前端 | `web-student/src/views/profile/index.vue` | 194 | 保存会覆盖空字符串、缺修改密码入口 | 加字段过滤 + 改密入口 |
| 26 | 后端 | `training-admin/.../RbacDataInitializer.java` | 79 | `@PostConstruct` 混 `@Transactional`，代理可能未生成 | 拆分 init / run |
| 27 | 后端 | `training-dao/.../SysRolePermissionMapper.xml` | 6 | `sys_role_permission` 物理 DELETE，与全局逻辑删除冲突 | 改逻辑删除 |
| 28 | 后端 | `training-service/.../ChapterServiceImpl.java` | 59 | resort 存在 N+1 更新 | 批量更新 |
| 29 | 后端 | `training-api/.../PlanApiController.java` / `CourseApiController.java` | 109 / 130 | 服务端异常一律吞成 404 | 区分 404 / 500 |
| 30 | 后端 | `training-dao/.../Question.java` / `Exam.java` | 31 / 33 | 业务字段无校验注解 | 加 `@NotBlank` / `@Size` 等 |

---

## 四、改进建议（可选，答辩加分）

1. 考试提交时把 `clientStartTime` / `clientEndTime` 一并提交，后端做交叉校验（防本地时钟篡改）
2. 成绩数据改走 Pinia / 状态管理或后端 session，不走 URL query
3. 管理后台 URL 改 `import.meta.env.VITE_ADMIN_URL` 环境变量
4. `exam.status` 抽到 `dict.js` 成枚举常量
5. 删除 `reviewList` 或真正实现「错题回看」UI（答辩加分项）
6. 自动阅卷 `autoGrade()` 加 JUnit 单元测试覆盖（多选 / 判断 / 填空边界）
7. 课程列表加分页缓存 / 大列表虚拟滚动

---

## 五、亮点（做得好的地方，答辩可加分）

- ✅ **SQL 全部 `#{}`**，**未发现 `${}` 注入点**
- ✅ 密码 BCrypt（strength 10），返回前统一 `setPassword(null)`
- ✅ `GlobalExceptionHandler` 统一兜底，业务异常不暴露堆栈
- ✅ 全部 admin 接口 `@PreAuthorize` + SecurityConfig URL 双层
- ✅ 17 张表配 `@TableLogic` 逻辑删除
- ✅ 前端 Composition API 全面、统一 request、路由守卫、401 自动跳转
- ✅ 考试计时用「结束时间戳」模式（防标签页节流），`onUnmounted` 清理
- ✅ 真实数据优先 + mock 兜底（chart、home 统计）的 graceful fallback
- ✅ 字典工具 `dict.js` 收口、`format.js` 统一时间 / 数字格式化

---

## 六、Top 5 优先修（按答辩风险排序）

1. **JWT secret + DB 凭证**：改 256-bit 随机值 + 从环境变量读取（`${JWT_SECRET}` / `${DB_PASSWORD}`），`.gitignore` 敏感配置
2. **submitExam 并发防重**：加 `UPDATE ... WHERE id=? AND status=0` 乐观锁判断影响行
3. **越权修复**：transferHuman / recordDetail / admin resetPassword 加归属校验
4. **考试成绩不走 URL query**：改 Pinia / session 或后端签名，提交时带 `clientStartTime/clientEndTime` 交叉校验
5. **删调试日志 + 注释**：移除判题 info 日志 + 泄露端口 / 僵尸文字的注释

---

## 七、纠正项（本报告对评审子代理输出的修正）

| 原报告描述 | 纠正后 |
|---|---|
| 「注释泄漏后端端口 8080/8081」 | ⚠️ 注释是**过时残留**（实际已迁到 9898/9899），但「8081 端口被僵尸进程死锁」这种**故障排查过程文字**留在生产代码里，答辩被看到会扣分。**不属安全泄露**（都是 localhost），属工程规范问题 |
| 「属于安全泄露」 | 降级为「代码不专业 / 工程过程痕迹残留」 |

**实际位置**：
- `web-student/src/api/user.js:4,8,12` — 注释里写 `8080`/`8081`，实际是 `9898`/`9899`
- `web-student/vite.config.*:28` — 注释「8081 端口被僵尸进程死锁，改用 9899」

---

## 八、结论

- **MVP 功能链路完整**：登录 / 课程列表 / 详情 / 学习 / 考试答题 / 结果 / 个人中心 / 培训计划 / 咨询 / 讲师工作台全部齐备，对接真实后端
- **安全 / 正确性**是短板：考试防作弊、成绩伪造、越权、JWT 弱密钥是答辩必问，建议优先修
- 修复 Top 5 后可进入联调验收；其余警告建议 1-2 天内处理

---

## 附录：相关文件路径

**后端**：
- `training-parent/training-admin/src/main/resources/application.yml`
- `training-parent/training-api/src/main/resources/application.yml`
- `training-parent/training-service/src/main/java/com/training/service/exam/ExamBizServiceImpl.java`
- `training-parent/training-service/src/main/java/com/training/service/exam/ExamServiceImpl.java`
- `training-parent/training-service/src/main/java/com/training/service/course/CourseServiceImpl.java`
- `training-parent/training-service/src/main/java/com/training/service/consult/ConsultServiceImpl.java`
- `training-parent/training-api/src/main/java/com/training/api/controller/WxAuthController.java`
- `training-parent/training-api/src/main/java/com/training/api/controller/ExamApiController.java`
- `training-parent/training-admin/src/main/java/com/training/admin/controller/UserManagementController.java`
- `training-parent/training-admin/src/main/java/com/training/admin/controller/CourseController.java`
- `training-parent/training-admin/src/main/java/com/training/admin/security/JwtAuthenticationFilter.java`
- `training-parent/training-common/src/main/java/com/training/common/utils/JwtUtils.java`
- `training-parent/training-dao/src/main/resources/mapper/StudyRecordMapper.xml`
- `training-parent/training-dao/src/main/resources/mapper/QuestionMapper.xml`
- `training-parent/training-dao/src/main/resources/mapper/SysRolePermissionMapper.xml`

**前端**：
- `web-student/src/views/exam/answer.vue`
- `web-student/src/views/exam/result.vue`
- `web-student/src/views/exam/list.vue`
- `web-student/src/views/login/index.vue`
- `web-student/src/views/home/index.vue`
- `web-student/src/views/course/{list,detail,learn}.vue`
- `web-student/src/views/profile/index.vue`
- `web-student/src/views/layouts/MainLayout.vue`
- `web-student/src/router/index.js`
- `web-student/src/api/user.js`
- `web-student/src/utils/request.js`
- `web-student/vite.config.*`
