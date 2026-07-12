-- ============================================================
-- fix_data_corruption.sql
-- Windows PowerShell -e "..." 双引号字符串会让中文被 PS 解释成系统代码页 (GBK) 然后传给 mysql.exe
-- 导致 question #9 标题、consult_record answer 等 UTF-8 中文字段被 0x3F (ASCII '?') 损坏
-- 本文件用 UNHEX (十六进制直接注入) 避开 PS 字符编码
--
-- 用法: Get-Content -Raw -Encoding UTF8 docs/fix_data_corruption.sql | mysql -uroot -proot --default-character-set=utf8mb4
-- ============================================================
SET NAMES utf8mb4;
USE training;

-- ==== question #9 title (UTF-8 hex) ====
-- 高血压患者血压控制目标一般为＜（）mmHg
UPDATE question SET
  title = UNHEX('E9AB98E8A180E58E8BE682A3E88085E8A180E58E8BE68EA7E588B6E79BAEE6A087E4B880E888ACE4B8BAEFBC9CEFBC886D6D4867'),
  options = '["A. 130/85","B. 140/90","C. 150/95","D. 160/100"]',
  answer = 'B'
WHERE id = 9;

-- ==== consult_record 重新 完整插入 ====
TRUNCATE TABLE consult_record;
INSERT INTO consult_record (id,student_id,question,answer,is_auto,create_time,reply_time) VALUES
(1, 4,
 '如何重考不及格的考试?',
 '登录后进入''考试中心''，找到不及格考试，若 max_retry 未用尽可点''重考''按钮。剩余重考次数以考试设置为准。',
 2, '2026-07-09 09:00:00', '2026-07-09 09:00:45'),
(2, 6,
 '离线课件下载后怎么观看?',
 '进入''课程详情''页，点击''下载''保存 ZIP 包（仅 offline_filename 非空的课程支持），离线状态下仍可进入课程播放页',
 2, '2026-07-09 10:30:00', '2026-07-09 10:30:38'),
(3, 8,
 '少数民族语言课程什么时候上线?',
 '目前平台已完成多语言接口预留，具体语言包上线以省卫健委公告为准。已转产品开发团队跟进。',
 0, '2026-07-09 14:00:00', NULL);

-- ==== 校验输出 ====
SELECT '=== checksum ===' AS log;
SELECT id, HEX(title) FROM question WHERE id=9;
SELECT id, LEFT(answer, 20), HEX(answer) FROM consult_record;
