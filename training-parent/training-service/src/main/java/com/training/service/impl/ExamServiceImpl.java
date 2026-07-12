package com.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.common.dto.ExamPageQuery;
import com.training.common.entity.Exam;
import com.training.common.entity.Question;
import com.training.common.exception.BusinessException;
import com.training.common.result.ResultCode;
import com.training.mapper.ExamMapper;
import com.training.mapper.QuestionMapper;
import com.training.service.ExamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 考试服务实现
 */
@Slf4j
@Service
public class ExamServiceImpl extends ServiceImpl<ExamMapper, Exam> implements ExamService {

    @Resource
    private QuestionMapper questionMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public IPage<Exam> page(ExamPageQuery query) {
        Page<Exam> page = new Page<>(query.getPageNum(), query.getPageSize());
        return baseMapper.selectExamPage(page, query.getTitle(), query.getCourseId(), query.getExamType(), query.getStatus());
    }

    @Override
    public boolean create(Exam exam) {
        if (exam.getStatus() == null) {
            exam.setStatus(0);
        }
        if (exam.getTotalScore() == null) {
            exam.setTotalScore(100);
        }
        if (exam.getPassScore() == null) {
            exam.setPassScore(60);
        }
        if (exam.getDuration() == null) {
            exam.setDuration(120);
        }
        if (exam.getMaxRetry() == null) {
            exam.setMaxRetry(1);
        }
        if (exam.getQuestionCount() == null) {
            exam.setQuestionCount(20);
        }
        // M11-P0-1: 创建考试时若未指定 questionIds 则自动组卷(按 courseId 优先 + 全库补足)
        if (exam.getQuestionIds() == null || exam.getQuestionIds().trim().isEmpty()) {
            autoFillQuestionIds(exam);
        }
        return save(exam);
    }

    @Override
    public boolean updateExam(Exam exam) {
        if (exam.getId() == null) {
            throw new IllegalArgumentException("考试ID不能为空");
        }
        Exam exist = getById(exam.getId());
        if (exist == null) {
            throw new IllegalArgumentException("考试不存在");
        }
        // M11-P0-1: 更新考试时若 questionIds 显式置空字符串则重新组卷
        if (exam.getQuestionIds() != null && exam.getQuestionIds().trim().isEmpty()) {
            // 用已有 exam 信息(未传 courseId 时保留原值)组卷
            if (exam.getCourseId() == null) {
                exam.setCourseId(exist.getCourseId());
            }
            if (exam.getQuestionCount() == null) {
                exam.setQuestionCount(exist.getQuestionCount());
            }
            autoFillQuestionIds(exam);
        }
        return updateById(exam);
    }

    @Override
    public boolean publish(Long examId) {
        if (examId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "考试ID不能为空");
        }
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND.getCode(), "考试不存在");
        }
        // M12 修复：允许 status=0(草稿) / 2(已下架) → 1(已发布) 任意状态切换
        if (exam.getStatus() != null && exam.getStatus() == 1) {
            return true; // 已是已发布，幂等返回
        }
        // 兜底：发布前若 questionIds 为空，尝试一次自动组卷
        if (exam.getQuestionIds() == null || exam.getQuestionIds().trim().isEmpty()) {
            try {
                autoFillQuestionIds(exam);
            } catch (Exception e) {
                log.warn("P1-5 发布考试自动组卷失败 examId={} {}", examId, e.getMessage());
            }
        }
        // 最终校验：发布必须至少 1 道题
        if (exam.getQuestionIds() == null || exam.getQuestionIds().trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "题库为空，无法发布：请先组卷或添加题目");
        }
        exam.setStatus(1); // 0 草稿 / 2 已下架 -> 1 已发布
        boolean ok = updateById(exam);
        log.info("M12 考试重新发布 examId={} 原 status={} -> 1", examId,
                exam.getStatus() != null ? exam.getStatus() : "null");
        return ok;
    }

    @Override
    public boolean offline(Long examId) {
        if (examId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "考试ID不能为空");
        }
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND.getCode(), "考试不存在");
        }
        exam.setStatus(2); // 1 已发布 -> 2 已下架
        return updateById(exam);
    }

    /**
     * M11-P0-1: 自动组卷填充 questionIds
     *
     * 抽题策略:
     *   1) 有 courseId -> 优先从该课程下客观题随机抽(题数 = questionCount)
     *   2) 题数不足 -> 从全库客观题随机补足
     *   3) 仍不足 -> 取已抽到的(允许题数偏少,但保证不抛异常)
     *   4) 全库也无题 -> questionIds 留 null(不阻塞保存)
     *
     * 注意:仅抽客观题(question_type 1,2,3),问答题留给人工出题
     */
    private void autoFillQuestionIds(Exam exam) {
        int need = exam.getQuestionCount() == null || exam.getQuestionCount() <= 0
                ? 10 : exam.getQuestionCount();

        // 1) 课程题库(仅客观题)
        List<Question> coursePool = new ArrayList<>();
        if (exam.getCourseId() != null) {
            coursePool = questionMapper.selectList(
                    new LambdaQueryWrapper<Question>()
                            .eq(Question::getCourseId, exam.getCourseId())
                            .in(Question::getQuestionType, 1, 2, 3)
            );
        }

        List<Long> picked = new ArrayList<>();
        if (!coursePool.isEmpty()) {
            int n = Math.min(need, coursePool.size());
            Collections.shuffle(coursePool);
            picked = coursePool.subList(0, n).stream()
                    .map(Question::getId)
                    .collect(Collectors.toList());
            log.info("M11-P0-1 自动组卷 exam.title={} courseId={} 抽课程题 {} 道(题库共 {} 道)",
                    exam.getTitle(), exam.getCourseId(), picked.size(), coursePool.size());
        }

        // 2) 题数不足,全库补足
        if (picked.size() < need) {
            int remain = need - picked.size();
            List<Question> globalPool = questionMapper.selectList(
                    new LambdaQueryWrapper<Question>()
                            .in(Question::getQuestionType, 1, 2, 3)
                            .notIn(Question::getId, picked)
            );
            if (!globalPool.isEmpty()) {
                Collections.shuffle(globalPool);
                int n = Math.min(remain, globalPool.size());
                List<Long> supplement = globalPool.subList(0, n).stream()
                        .map(Question::getId)
                        .collect(Collectors.toList());
                picked.addAll(supplement);
                log.info("M11-P0-1 自动组卷 全库补足 {} 道(总 {} 道, 仍差 {} 道)",
                        supplement.size(), picked.size(), need - picked.size());
            }
        }

        if (picked.isEmpty()) {
            log.warn("M11-P0-1 自动组卷 题库为空,exam.title={} 不预填 questionIds(由 startExam 兜底抽题)",
                    exam.getTitle());
            return;
        }

        // 3) 同步修正 question_count 为实际抽到数量(避免前端显示与实际不一致)
        exam.setQuestionCount(picked.size());
        try {
            exam.setQuestionIds(OBJECT_MAPPER.writeValueAsString(picked));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVER_ERROR.getCode(), "questionIds 序列化失败");
        }
        log.info("M11-P0-1 自动组卷完成 exam.title={} questionCount={} ids={}",
                exam.getTitle(), picked.size(), picked);
    }
}
