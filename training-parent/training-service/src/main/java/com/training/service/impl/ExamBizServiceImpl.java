package com.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.training.common.dto.AnswerDTO;
import com.training.common.entity.*;
import com.training.common.exception.BusinessException;
import com.training.common.result.PageResult;
import com.training.common.result.ResultCode;
import com.training.common.vo.ExamListVO;
import com.training.common.vo.ExamResultVO;
import com.training.common.vo.ExamStartVO;
import com.training.common.vo.GenerateResultVO;
import com.training.common.vo.PaperQuestionVO;
import com.training.mapper.ExamAnswerMapper;
import com.training.mapper.ExamMapper;
import com.training.mapper.ExamPaperMapper;
import com.training.mapper.ExamRecordMapper;
import com.training.mapper.QuestionMapper;
import com.training.mapper.CourseEnrollMapper;
import com.training.service.ExamBizService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 考试业务服务实现
 *
 * 核心算法：
 * 1. 自动组卷：按难度比例 30:50:20 从指定知识点题库随机抽题
 * 2. 自动阅卷：客观题（单选/多选/判断/填空）自动判分，问答题标记待人工批阅
 */
@Slf4j
@Service
public class ExamBizServiceImpl implements ExamBizService {

    @Resource
    private ExamMapper examMapper;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private ExamPaperMapper examPaperMapper;

    @Resource
    private ExamRecordMapper examRecordMapper;

    @Resource
    private ExamAnswerMapper examAnswerMapper;

    @Resource
    private CourseEnrollMapper courseEnrollMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 难度比例：简单 30%、普通 50%、困难 20%
    private static final double EASY_RATIO = 0.3;
    private static final double MEDIUM_RATIO = 0.5;

    @Override
    public List<Long> autoGeneratePaper(Long examId, List<Long> knowledgePointIds) {
        // 1. 校验考试
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND);
        }
        if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "知识点ID列表不能为空");
        }

        // 2. 收集候选题目（按知识点）
        List<Question> allQuestions = questionMapper.selectByKnowledgePoints(knowledgePointIds);
        if (allQuestions.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "该知识点题库为空，无法组卷");
        }
        // 题库不足时自动缩小到实际数量，保证演示能跑通
        int actualCount = Math.min(exam.getQuestionCount(), allQuestions.size());

        // 3. 按难度分桶
        Map<Integer, List<Question>> byDifficulty = allQuestions.stream()
                .collect(Collectors.groupingBy(Question::getDifficulty));

        // 4. 计算各难度题目数量（简单 30%、普通 50%、困难 20%）
        int easyCount = (int) Math.ceil(actualCount * EASY_RATIO);
        int mediumCount = (int) Math.ceil(actualCount * MEDIUM_RATIO);
        int hardCount = actualCount - easyCount - mediumCount;

        // 5. 各桶随机抽题
        List<Long> easy = randomPick(byDifficulty.getOrDefault(1, Collections.emptyList()), easyCount);
        List<Long> medium = randomPick(byDifficulty.getOrDefault(2, Collections.emptyList()), mediumCount);
        List<Long> hard = randomPick(byDifficulty.getOrDefault(3, Collections.emptyList()), hardCount);

        // 6. 合并并打乱顺序
        List<Long> paper = new ArrayList<>();
        paper.addAll(easy);
        paper.addAll(medium);
        paper.addAll(hard);
        Collections.shuffle(paper);

        log.info("自动组卷完成：examId={}, 题目数={}, 简单={}, 普通={}, 困难={}",
                examId, paper.size(), easy.size(), medium.size(), hard.size());
        return paper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamStartVO startExam(Long examId, Long studentId) {
        // 1. 校验考试
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND);
        }
        if (exam.getStatus() == null || exam.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "考试未发布");
        }

        // 2. 校验时间窗口
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "考试尚未开始");
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            throw new BusinessException(ResultCode.EXAM_TIME_OVER);
        }

        // 3. 校验重考次数（已提交/已批阅的记录数）
        int finishedCount = examRecordMapper.countFinishedByExamAndStudent(examId, studentId);
        if (finishedCount >= exam.getMaxRetry()) {
            throw new BusinessException(ResultCode.RETRY_LIMIT);
        }

        // 4. 查找或生成试卷（同一学员同一考试复用已生成试卷）
        ExamPaper paper = examPaperMapper.selectOne(
                new LambdaQueryWrapper<ExamPaper>()
                        .eq(ExamPaper::getExamId, examId)
                        .eq(ExamPaper::getStudentId, studentId)
        );

        List<Long> questionIds;
        if (paper == null) {
            // M11-P0-1: 优先使用 exam.questionIds 预组卷模板(若非空且题目仍存在)
            List<Long> presetIds = parsePresetQuestionIds(exam);
            if (!presetIds.isEmpty()) {
                questionIds = presetIds;
                log.info("M11-P0-1 startExam 命中 exam.questionIds 预组卷 examId={} 题数={}", examId, questionIds.size());
            } else {
                // 抽题策略（MVP 兜底）：
                //   1) 先尝试"按课程关联知识点抽题"
                //   2) 课程下无知识点 → 从课程下全量题目随机抽
                //   3) 课程下也无题目 → 单独考试(courseId 为空)从全库题目抽
                //   4) 都抽不到 → 报错
                List<Long> kpIds = loadKnowledgePointIdsByExam(exam);
                if (!kpIds.isEmpty()) {
                    questionIds = autoGeneratePaper(examId, kpIds);
                } else {
                    // fallback：知识点为空，按课程全库 / 全库题目随机抽
                    questionIds = randomPickAll(exam);
                    if (questionIds.isEmpty()) {
                        throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                                "考试题库为空，无法组卷：examId=" + examId);
                    }
                }
            }

            // 保存试卷
            paper = new ExamPaper();
            paper.setExamId(examId);
            paper.setStudentId(studentId);
            try {
                paper.setQuestions(OBJECT_MAPPER.writeValueAsString(questionIds));
            } catch (Exception e) {
                throw new BusinessException(ResultCode.SERVER_ERROR.getCode(), "试卷序列化失败");
            }
            examPaperMapper.insert(paper);
        } else {
            // 复用已有试卷
            try {
                questionIds = OBJECT_MAPPER.readValue(paper.getQuestions(), new TypeReference<List<Long>>() {});
            } catch (Exception e) {
                throw new BusinessException(ResultCode.SERVER_ERROR.getCode(), "试卷反序列化失败");
            }
        }

        // 5. [状态机修复] 检查是否有进行中记录（status=0），决定复用 / 视为放弃 / 新建
        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, studentId)
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStatus, 0)
                        .orderByDesc(ExamRecord::getCreateTime)
                        .last("LIMIT 1")
        );

        if (record != null) {
            // 计算是否已超时：start_time + duration 分钟 > now 表示未超时
            int durationMin = exam.getDuration() != null ? exam.getDuration() : 60;
            LocalDateTime deadline = record.getStartTime() != null
                    ? record.getStartTime().plusMinutes(durationMin)
                    : null;

            if (deadline != null && deadline.isAfter(now)) {
                // 未超时：复用进行中记录，用其 paperId 重新加载题目（防止 paper 被换）
                ExamPaper reusePaper = examPaperMapper.selectById(record.getPaperId());
                if (reusePaper == null) {
                    throw new BusinessException(ResultCode.SERVER_ERROR.getCode(),
                            "进行中记录对应的试卷不存在：paperId=" + record.getPaperId());
                }
                List<Long> reuseQuestionIds;
                try {
                    reuseQuestionIds = OBJECT_MAPPER.readValue(reusePaper.getQuestions(),
                            new TypeReference<List<Long>>() {});
                } catch (Exception e) {
                    throw new BusinessException(ResultCode.SERVER_ERROR.getCode(), "试卷反序列化失败");
                }

                // 加载题目（不含答案）
                List<Question> reuseQuestions = questionMapper.selectBatchIds(reuseQuestionIds);
                Map<Long, Question> reuseQuestionMap = reuseQuestions.stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));
                List<PaperQuestionVO> reuseQuestionVOs = new ArrayList<>();
                for (Long qid : reuseQuestionIds) {
                    Question q = reuseQuestionMap.get(qid);
                    if (q == null) continue;
                    PaperQuestionVO pqvo = new PaperQuestionVO();
                    pqvo.setId(q.getId());
                    pqvo.setTitle(q.getTitle());
                    pqvo.setQuestionType(q.getQuestionType());
                    pqvo.setOptions(q.getOptions());
                    pqvo.setScore(q.getScore());
                    reuseQuestionVOs.add(pqvo);
                }

                // 组装返回（与原返回结构完全一致）
                ExamStartVO reuseVo = new ExamStartVO();
                reuseVo.setExamId(examId);
                reuseVo.setTitle(exam.getTitle());
                reuseVo.setDuration(exam.getDuration());
                reuseVo.setTotalScore(exam.getTotalScore());
                reuseVo.setPassScore(exam.getPassScore());
                reuseVo.setQuestions(reuseQuestionVOs);
                reuseVo.setStartTime(record.getStartTime());
                reuseVo.setServerTime(now);

                log.info("学员 {} 复用进行中考试记录 {}，examId={}, paperId={}, startTime={}",
                        studentId, record.getId(), examId, record.getPaperId(), record.getStartTime());
                return reuseVo;
            } else {
                // 已超时：把旧记录标记为 status=2（视为放弃，得分 0），然后创建新记录
                record.setStatus(2);
                record.setScore(0);
                record.setSubmitTime(now);
                examRecordMapper.updateById(record);
                log.info("学员 {} 进行中考试记录 {} 已超时，标记为放弃 status=2，将创建新记录",
                        studentId, record.getId());
                record = null; // 清空，下面新建
            }
        }

        // 6. 创建考试记录（进行中）— 无进行中记录或旧记录已超时放弃
        record = new ExamRecord();
        record.setStudentId(studentId);
        record.setExamId(examId);
        record.setPaperId(paper.getId());
        record.setScore(0);
        record.setStatus(0);
        record.setStartTime(now);
        examRecordMapper.insert(record);

        // 7. 加载题目（不含答案）
        List<Question> questions = questionMapper.selectBatchIds(questionIds);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<PaperQuestionVO> questionVOs = new ArrayList<>();
        for (Long qid : questionIds) {
            Question q = questionMap.get(qid);
            if (q == null) continue;
            PaperQuestionVO vo = new PaperQuestionVO();
            vo.setId(q.getId());
            vo.setTitle(q.getTitle());
            vo.setQuestionType(q.getQuestionType());
            vo.setOptions(q.getOptions());
            vo.setScore(q.getScore());
            questionVOs.add(vo);
        }

        // 8. 组装返回
        ExamStartVO vo = new ExamStartVO();
        vo.setExamId(examId);
        vo.setTitle(exam.getTitle());
        vo.setDuration(exam.getDuration());
        vo.setTotalScore(exam.getTotalScore());
        vo.setPassScore(exam.getPassScore());
        vo.setQuestions(questionVOs);
        // 双保险计时：把 ExamRecord.startTime 和服务端当前时间一起回传
        // 作用：
        //   1) 前端基于 startTime + duration 倒计时，避免本地时钟漂移
        //   2) 前端基于 serverTime 校时（防止学员改本地时间作弊）
        vo.setStartTime(record.getStartTime());
        vo.setServerTime(now);

        log.info("学员 {} 开始考试 {}，试卷ID={}, 题目数={}, startTime={}",
                studentId, examId, paper.getId(), questionVOs.size(), record.getStartTime());
        return vo;
    }

    /**
     * 管理员自动组卷并持久化模板试卷
     *
     * 模板试卷以 studentId = 0L 作为唯一标识（同一考试仅一条模板），
     * upsert 保证管理员重复生成时更新而非重复插入。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateResultVO generateAndSavePaper(Long examId, List<Long> knowledgePointIds, Long adminId) {
        // 1. 调用已有组卷算法抽题（不动原方法，避免影响 startExam）
        List<Long> questionIds = autoGeneratePaper(examId, knowledgePointIds);

        // 2. 序列化题目列表
        String questionsJson;
        try {
            questionsJson = OBJECT_MAPPER.writeValueAsString(questionIds);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVER_ERROR.getCode(), "试卷序列化失败");
        }

        // 3. 查找是否已有模板卷（studentId = 0L）
        ExamPaper existing = examPaperMapper.selectOne(
                new LambdaQueryWrapper<ExamPaper>()
                        .eq(ExamPaper::getExamId, examId)
                        .eq(ExamPaper::getStudentId, 0L)
        );

        Long paperId;
        if (existing == null) {
            ExamPaper paper = new ExamPaper();
            paper.setExamId(examId);
            paper.setStudentId(0L);
            paper.setQuestions(questionsJson);
            paper.setCreateTime(LocalDateTime.now());
            examPaperMapper.insert(paper);
            paperId = paper.getId();
        } else {
            existing.setQuestions(questionsJson);
            existing.setCreateTime(LocalDateTime.now());
            examPaperMapper.updateById(existing);
            paperId = existing.getId();
        }

        log.info("管理员 {} 生成考试 {} 模板试卷，题目数={}, paperId={}",
                adminId, examId, questionIds.size(), paperId);

        // 4. 组装返回
        GenerateResultVO vo = new GenerateResultVO();
        vo.setQuestionIds(questionIds);
        vo.setQuestionCount(questionIds.size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamResultVO submitExam(Long examId, Long studentId, List<AnswerDTO> answers,
                                    Long clientStartTime, Long clientEndTime) {
        // 1. 查找进行中的考试记录
        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, studentId)
                        .eq(ExamRecord::getExamId, examId)
                        .orderByDesc(ExamRecord::getCreateTime)
                        .last("LIMIT 1")
        );
        if (record == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "没有进行中的考试记录");
        }
        if (record.getStatus() != null && record.getStatus() == 2) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "本场考试已提交过");
        }

        // 1.1 [并发防重 / #2 评审项] 乐观锁 CAS：仅当 status=0 时抢占为 status=1（已提交待批阅）。
        // 并发双写时只有一个线程能命中 status=0，另一个 affected=0 抛业务异常，避免双插 exam_answer / 成绩被覆盖。
        LocalDateTime submitTime = LocalDateTime.now();
        int affected = examRecordMapper.update(null,
                new LambdaUpdateWrapper<ExamRecord>()
                        .eq(ExamRecord::getId, record.getId())
                        .eq(ExamRecord::getStatus, 0)
                        .set(ExamRecord::getStatus, 1)
                        .set(ExamRecord::getSubmitTime, submitTime)
        );
        if (affected == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "考试已提交，请勿重复操作");
        }
        // 同步本地对象状态（后续阅卷会再次 update 到 status=2）
        record.setStatus(1);
        record.setSubmitTime(submitTime);

        // 2. 校验时间窗口（超时自动提交，但提示）
        Exam exam = examMapper.selectById(examId);
        LocalDateTime now = submitTime;
        if (exam != null && exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            log.warn("学员 {} 提交考试 {} 时已超出结束时间，自动提交", studentId, examId);
        }

        // 2.1 [时间戳交叉校验 / #3 评审项] 防止用户改系统时钟作弊
        //   - serverStartTime = examRecord.start_time（服务端落库，不可篡改）
        //   - serverAllowedDuration = exam.duration 分钟
        //   - tolerance = 60 秒（容忍时钟漂移 / 网络延迟）
        //   - 超时 > 5 分钟（300 秒）直接判违规 score=0；超时但 <= 5 分钟标记违规但允许提交（不直接失败，避免误伤正常用户）
        boolean forceZeroScore = false;
        if (clientStartTime != null && clientEndTime != null && record.getStartTime() != null && exam != null) {
            long serverStartMillis = record.getStartTime()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            int durationMin = exam.getDuration() != null ? exam.getDuration() : 60;
            long serverAllowedMillis = durationMin * 60L * 1000L;
            long toleranceMillis = 60L * 1000L; // 60 秒容差

            // 容错：客户端开考时间与服务端开考时间偏差过大仅记 warning（可能终端时钟不同步）
            long startDelta = Math.abs(clientStartTime - serverStartMillis);
            if (startDelta > 5L * 60L * 1000L) {
                log.warn("[考试违规检测] 学员 {} 考试 {} clientStartTime 与 serverStartTime 偏差 {}ms > 5min，疑似终端时钟异常",
                        studentId, examId, startDelta);
            }

            long clientDuration = clientEndTime - clientStartTime;
            long allowedUpperBound = serverAllowedMillis + toleranceMillis;
            if (clientDuration > allowedUpperBound) {
                long overMillis = clientDuration - serverAllowedMillis;
                if (overMillis > 5L * 60L * 1000L) {
                    // 超时 > 5 分钟，直接判违规 score=0
                    forceZeroScore = true;
                    log.warn("[考试违规检测] 学员 {} 考试 {} 客户端实际作答时长 {}ms 超出服务端允许 {}ms 达 {}ms（>5min），判违规 score=0",
                            studentId, examId, clientDuration, serverAllowedMillis, overMillis);
                } else {
                    // 超时但 <= 5 分钟，标记违规但允许提交（避免误伤）
                    log.warn("[考试违规检测] 学员 {} 考试 {} 客户端实际作答时长 {}ms 超出服务端允许 {}ms 达 {}ms（<=5min），标记违规但允许提交",
                            studentId, examId, clientDuration, serverAllowedMillis, overMillis);
                }
            }
        }

        // 3. 加载试卷题目
        ExamPaper paper = examPaperMapper.selectById(record.getPaperId());
        List<Long> questionIds;
        try {
            questionIds = OBJECT_MAPPER.readValue(paper.getQuestions(), new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVER_ERROR.getCode(), "试卷反序列化失败");
        }

        // 4. 加载题目详情（含答案）
        List<Question> questions = questionMapper.selectBatchIds(questionIds);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 5. 自动阅卷
        int totalScore = 0;
        int earnedScore = 0;
        int correctCount = 0;
        int wrongCount = 0;

        Map<Long, AnswerDTO> answerMap = new HashMap<>();
        if (answers != null) {
            for (AnswerDTO a : answers) {
                answerMap.put(a.getQuestionId(), a);
            }
        }

        for (Long qid : questionIds) {
            Question q = questionMap.get(qid);
            if (q == null) continue;

            AnswerDTO userAnswer = answerMap.get(qid);
            String userAns = userAnswer != null ? userAnswer.getAnswer() : null;

            ExamAnswer ea = new ExamAnswer();
            ea.setRecordId(record.getId());
            ea.setQuestionId(q.getId());
            ea.setStudentAnswer(userAns);

            totalScore += (q.getScore() == null ? 0 : q.getScore());

            if (q.getQuestionType() != null && q.getQuestionType() == 5) {
                // 问答题：待人工批阅
                ea.setIsCorrect(null);
                ea.setScore(0);
            } else {
                // 客观题：自动判分
                boolean correct = checkAnswer(q, userAns);
                ea.setIsCorrect(correct ? 1 : 0);
                int score = correct ? (q.getScore() == null ? 0 : q.getScore()) : 0;
                ea.setScore(score);
                earnedScore += score;
                if (correct) correctCount++; else wrongCount++;
            }
            examAnswerMapper.insert(ea);
        }

        // 6. 更新考试记录
        // [百分制改造] 统一采用 0-100 百分制得分，与 exam.passScore（百分比）语义对齐
        // 8 题答对 2 题 = 25 分（2/8 * 100），避免绝对分与百分比及格线错配
        int percentScore = totalScore > 0
                ? (int) Math.round((double) earnedScore / totalScore * 100)
                : 0;
        // [时间戳交叉校验 / #3 评审项] 严重违规（超时 > 5 分钟）强制 0 分
        if (forceZeroScore) {
            percentScore = 0;
        }
        record.setScore(percentScore);
        record.setStatus(2); // 已批阅
        record.setSubmitTime(now);
        examRecordMapper.updateById(record);

        // 7. 组装返回（统一百分制：满分 100，得分 0-100）
        ExamResultVO vo = new ExamResultVO();
        vo.setScore(percentScore);
        vo.setTotalScore(100);
        // passScore 为百分比（如 60 表示 60%），直接与百分制得分比较
        int passScore = exam != null && exam.getPassScore() != null ? exam.getPassScore() : 60;
        vo.setPassed(percentScore >= passScore);
        vo.setCorrectCount(correctCount);
        vo.setWrongCount(wrongCount);
        // correctRate 已是 0-100 百分比（与 score 同语义）
        vo.setCorrectRate((double) percentScore);

        log.info("学员 {} 提交考试 {}：百分制得分={}/100 (原始 {}/{}), 答对={}, 答错={}, 及格={}",
                studentId, examId, percentScore, earnedScore, totalScore, correctCount, wrongCount, vo.getPassed());
        return vo;
    }

    /**
     * 自动判分核心算法
     *
     * 1 单选：字符串相等（忽略大小写）
     * 2 多选：字符集合相等（顺序无关）
     * 3 判断：字符串相等（忽略大小写）
     * 4 填空：多个答案用 | 分隔，对任意一个就算对
     * 5 问答：不自动判分
     */
    private boolean checkAnswer(Question q, String userAnswer) {
        if (userAnswer == null) return false;
        String correct = q.getAnswer() == null ? "" : q.getAnswer().trim();
        String user = userAnswer.trim();

        if (correct.isEmpty() && user.isEmpty()) return true;
        if (correct.isEmpty() || user.isEmpty()) return false;

        switch (q.getQuestionType()) {
            case 1: // 单选
            case 3: // 判断
                return user.equalsIgnoreCase(correct);
            case 2: { // 多选：顺序无关，字符集合相等
                Set<Character> correctSet = correct.chars()
                        .mapToObj(c -> (char) c)
                        .collect(Collectors.toSet());
                Set<Character> userSet = user.chars()
                        .mapToObj(c -> (char) c)
                        .collect(Collectors.toSet());
                return correctSet.equals(userSet);
            }
            case 4: { // 填空：多个答案用 | 分隔，对任意一个就算对
                String[] accepts = correct.split("\\|");
                return Arrays.stream(accepts)
                        .anyMatch(a -> a.trim().equalsIgnoreCase(user));
            }
            default:
                return false;
        }
    }

    /**
     * 随机从题库中抽取指定数量的题目ID
     */
    private List<Long> randomPick(List<Question> pool, int count) {
        if (pool == null || pool.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        if (pool.size() <= count) {
            return pool.stream().map(Question::getId).collect(Collectors.toList());
        }
        Collections.shuffle(pool);
        return pool.subList(0, count).stream()
                .map(Question::getId)
                .collect(Collectors.toList());
    }

    /**
     * 从考试关联的题库中随机抽题（MVP 兜底：知识点关联缺失时的 fallback）
     *
     * 策略：
     *   - 有 courseId → 从该课程下所有题目随机抽
     *   - 无 courseId（单独考试）→ 从全库题目随机抽
     * 注意：不按难度比例，纯随机，保证演示能跑通
     */
    private List<Long> randomPickAll(Exam exam) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (exam.getCourseId() != null) {
            wrapper.eq(Question::getCourseId, exam.getCourseId());
        }
        wrapper.last("AND difficulty IN (1,2,3)"); // 仅客观题可自动阅卷
        List<Question> pool = questionMapper.selectList(wrapper);
        int count = exam.getQuestionCount() != null && exam.getQuestionCount() > 0
                ? exam.getQuestionCount() : 20;
        return randomPick(pool, count);
    }

    /**
     * 根据考试加载关联知识点ID列表
     *
     * 简化实现：从考试关联课程的所有知识点加载
     */
    private List<Long> loadKnowledgePointIdsByExam(Exam exam) {
        if (exam.getCourseId() == null) {
            return Collections.emptyList();
        }
        List<KnowledgePoint> kpList = new ArrayList<>();
        // 需要引入 KnowledgePointMapper，通过 courseId 查询
        // 这里用简单实现：直接查 question 表去重
        return questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getCourseId, exam.getCourseId())
        ).stream()
                .map(Question::getKnowledgePointId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * M11-P0-1: 解析 exam.questionIds 预组卷 JSON 数组
     *
     * 返回值: 题目ID列表(过滤掉已删除/不存在的题目)。为空表示无预组卷或预组卷已失效。
     */
    private List<Long> parsePresetQuestionIds(Exam exam) {
        if (exam == null || exam.getQuestionIds() == null || exam.getQuestionIds().trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> raw;
        try {
            raw = OBJECT_MAPPER.readValue(exam.getQuestionIds(), new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("M11-P0-1 exam.questionIds 解析失败 examId={} err={}", exam.getId(), e.getMessage());
            return Collections.emptyList();
        }
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        // 二次过滤: 移除已被逻辑删除的题目,避免返回不存在的题目
        List<Question> exist = questionMapper.selectBatchIds(raw);
        if (exist.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> existIds = exist.stream().map(Question::getId).collect(Collectors.toSet());
        List<Long> filtered = raw.stream()
                .filter(existIds::contains)
                .distinct()
                .collect(Collectors.toList());
        if (filtered.size() < raw.size()) {
            log.warn("M11-P0-1 exam.questionIds 存在已删题目 examId={} 原 {} 道 有效 {} 道",
                    exam.getId(), raw.size(), filtered.size());
        }
        return filtered;
    }

    /**
     * 学员维度考试列表 — P1-7 修复：支持分页
     *
     * 算法：
     * 1. 查出所有"考试本身已发布"（exam.status=1）且未删的考试
     * 2. 一次性批量查当前 userId 的 exam_record（不循环逐条查 DB）
     * 3. 按 examId 分组，取每组最新一条 record
     * 4. 计算学员维度 status：无记录→0；record.status=2→2；否则→1
     * 5. retryLeft = max(0, exam.maxRetry - 该 exam 的 record 数)
     * 6. 按学员维度 status 过滤后做内存分页
     */
    @Override
    public PageResult<ExamListVO> listForStudent(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        // 0. 参数归一化
        int pn = (pageNum == null || pageNum <= 0) ? 1 : pageNum;
        int ps = (pageSize == null || pageSize <= 0) ? 9 : pageSize;

        // 1. 查出所有已发布的考试
        List<Exam> exams = examMapper.selectList(
                new LambdaQueryWrapper<Exam>()
                        .eq(Exam::getStatus, 1)
                        .orderByDesc(Exam::getCreateTime)
        );
        if (exams.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0L, pn, ps);
        }

        // 2. 一次性批量查当前 userId 的 exam_record（不循环逐条查 DB）
        List<ExamRecord> allRecords = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, userId)
        );

        // 3. 批量查当前 userId 报名的课程（用于过滤课程考试）
        Set<Long> enrolledCourseIds = courseEnrollMapper.selectList(
                new LambdaQueryWrapper<CourseEnroll>()
                        .eq(CourseEnroll::getStudentId, userId)
        ).stream().map(CourseEnroll::getCourseId).collect(Collectors.toSet());

        // 4. 按 examId 分组，取每组最新一条（createTime 最大）
        Map<Long, List<ExamRecord>> recordsByExam = allRecords.stream()
                .collect(Collectors.groupingBy(ExamRecord::getExamId));

        // 5. 逐个 exam 组装 VO（按可见性过滤）
        List<ExamListVO> all = new ArrayList<>();
        for (Exam exam : exams) {
            // 可见性过滤：课程考试需报名对应课程，单独考试全员可见
            if (exam.getExamType() != null && exam.getExamType() == 1 && exam.getCourseId() != null
                    && !enrolledCourseIds.contains(exam.getCourseId())) {
                continue; // 课程考试，未报名该课程 → 不可见
            }

            ExamListVO vo = new ExamListVO();
            vo.setId(exam.getId());
            vo.setTitle(exam.getTitle());
            vo.setExamType(exam.getExamType());
            vo.setCourseId(exam.getCourseId());
            vo.setPlanId(exam.getPlanId());
            // [百分制改造] 列表展示统一为 100 分制，与 passScore（百分比）语义对齐
            vo.setTotalScore(100);
            vo.setPassScore(exam.getPassScore());
            vo.setDuration(exam.getDuration());
            vo.setMaxRetry(exam.getMaxRetry());
            vo.setQuestionCount(exam.getQuestionCount());

            List<ExamRecord> records = recordsByExam.getOrDefault(exam.getId(), Collections.emptyList());
            // 按 createTime DESC 排序，取最新一条
            records.sort(Comparator.comparing(ExamRecord::getCreateTime,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            int times = records.size();
            vo.setTimes(times);

            int maxRetry = exam.getMaxRetry() != null ? exam.getMaxRetry() : 0;
            // [状态机修复] retryLeft 只统计已批阅（status=2）的记录数，
            // 进行中（status=0）记录不应占用重考次数（学生放弃/超时后会标记为 status=2）
            int finishedCount = (int) records.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus() == 2)
                    .count();
            vo.setRetryLeft(Math.max(0, maxRetry - finishedCount));

            if (records.isEmpty()) {
                // 未开始
                vo.setStatus(0);
                vo.setCorrectCount(null);
                vo.setWrongCount(null);
                vo.setScore(null);
                vo.setPassed(null);
                vo.setRecordId(null);
            } else {
                ExamRecord latest = records.get(0);
                vo.setRecordId(latest.getId());
                Integer recordStatus = latest.getStatus();
                if (recordStatus != null && recordStatus == 2) {
                    // 已批阅
                    vo.setStatus(2);
                    vo.setScore(latest.getScore());
                    // passed 由 score >= passScore 推导
                    int passScore = exam.getPassScore() != null ? exam.getPassScore() : 60;
                    vo.setPassed(latest.getScore() != null && latest.getScore() >= passScore);
                    // correctCount/wrongCount 暂不维护（需聚合 exam_answer），列表页不关键，置 null
                    vo.setCorrectCount(null);
                    vo.setWrongCount(null);
                } else {
                    // 已提交等批阅（status=1 或 0 进行中但已有记录）
                    vo.setStatus(1);
                    vo.setScore(null);
                    vo.setPassed(null);
                    vo.setCorrectCount(null);
                    vo.setWrongCount(null);
                }
            }

            all.add(vo);
        }

        // 6. 按学员维度 status 过滤
        if (status != null) {
            all = all.stream()
                    .filter(v -> v.getStatus().equals(status))
                    .collect(Collectors.toList());
        }

        // 7. 内存分页（P1-7 修复：原实现直接返回全量列表，学员侧翻页无效果）
        long total = all.size();
        int fromIndex = Math.min((pn - 1) * ps, all.size());
        int toIndex = Math.min(fromIndex + ps, all.size());
        List<ExamListVO> pageData = all.subList(fromIndex, toIndex);
        return PageResult.of(pageData, total, pn, ps);
    }
}
