package com.training.api.controller;

import com.training.common.dto.ExamSubmitDTO;
import com.training.common.entity.Exam;
import com.training.common.entity.ExamAnswer;
import com.training.common.entity.ExamRecord;
import com.training.common.exception.BusinessException;
import com.training.common.result.PageResult;
import com.training.common.result.Result;
import com.training.common.result.ResultCode;
import com.training.common.vo.ExamListVO;
import com.training.common.vo.ExamResultVO;
import com.training.common.vo.ExamStartVO;
import com.training.mapper.ExamAnswerMapper;
import com.training.mapper.ExamRecordMapper;
import com.training.service.ExamBizService;
import com.training.service.ExamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 小程序考试接口（需要登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/exam")
public class ExamApiController {

    @Resource
    private ExamBizService examBizService;

    @Resource
    private ExamService examService;

    @Resource
    private ExamRecordMapper examRecordMapper;

    @Resource
    private ExamAnswerMapper examAnswerMapper;

    /**
     * 学员维度考试列表（含学员维度状态、剩余重考次数）— P1-7 修复：支持分页
     *
     * @param status   学员维度状态筛选：0未开始 1已完成(已提交) 2已批阅；null/不传表示全部
     * @param pageNum  页码（1-based），默认 1
     * @param pageSize 每页条数，默认 9
     * @param userId   当前学员ID（由拦截器注入）
     */
    @GetMapping("/list")
    public Result<PageResult<ExamListVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "9") Integer pageSize,
            @RequestAttribute("userId") Long userId) {
        return Result.success(examBizService.listForStudent(userId, status, pageNum, pageSize));
    }

    /**
     * 开始考试
     */
    @PostMapping("/start/{id}")
    public Result<ExamStartVO> start(@PathVariable Long id,
                                     @RequestAttribute("userId") Long userId) {
        ExamStartVO vo = examBizService.startExam(id, userId);
        return Result.success(vo);
    }

    /**
     * 提交考试（自动阅卷）
     */
    @PostMapping("/submit")
    public Result<ExamResultVO> submit(@RequestBody @Valid ExamSubmitDTO dto,
                                       @RequestAttribute("userId") Long userId) {
        ExamResultVO vo = examBizService.submitExam(dto.getExamId(), userId, dto.getAnswers());
        return Result.success(vo);
    }

    /**
     * 考试记录详情
     */
    @GetMapping("/record/{id}")
    public Result<ExamRecord> recordDetail(@PathVariable Long id) {
        ExamRecord record = examRecordMapper.selectById(id);
        if (record == null) {
            return Result.error(404, "考试记录不存在");
        }
        return Result.success(record);
    }

    /**
     * 我的考试记录列表
     */
    @GetMapping("/my-records")
    public Result<List<ExamRecord>> myRecords(@RequestAttribute("userId") Long userId) {
        List<ExamRecord> list = examRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, userId)
                        .orderByDesc(ExamRecord::getCreateTime)
        );
        return Result.success(list);
    }

    /**
     * 查看成绩（按 examId 查最新一条已批阅记录 + 聚合答题详情）
     * 用于"查看成绩"按钮跳转（无 query 参数场景）
     *
     * <p>M12 修复：
     * <ul>
     *   <li>原实现当 record=null 时返回 404，前端 result.vue 显示 alert 提示"加载失败"，体感"空白"</li>
     *   <li>改为：当 record=null 时也返回空 vo（score=0/correctCount=0），前端按"未参加考试"展示</li>
     *   <li>totalScore 从 exam.totalScore 取（修复原版误用 record.getScore() 当 totalScore 的 bug）</li>
     *   <li>passed 从 exam.passScore 判定</li>
     *   <li>correctRate 计算补全</li>
     * </ul>
     */
    @GetMapping("/result")
    public Result<ExamResultVO> result(@RequestParam Long examId,
                                       @RequestAttribute("userId") Long userId) {
        // 1) 取考试配置（用于 totalScore/passScore）
        Exam exam = examService.getById(examId);
        Integer totalScore = exam != null ? exam.getTotalScore() : 0;
        Integer passScore = exam != null ? exam.getPassScore() : 60;

        // 2) 取最新一条考试记录
        ExamRecord record = examRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, userId)
                        .eq(ExamRecord::getExamId, examId)
                        .orderByDesc(ExamRecord::getCreateTime)
                        .last("LIMIT 1")
        );

        ExamResultVO vo = new ExamResultVO();
        vo.setTotalScore(totalScore != null ? totalScore : 0);
        vo.setCorrectCount(0);
        vo.setWrongCount(0);
        vo.setUnansweredCount(0);
        vo.setCorrectRate(0.0);
        vo.setScore(0);
        vo.setPassed(false);

        // 3) 无记录：返回空 vo（前端展示"未参加考试"）
        if (record == null) {
            log.info("M12 exam/result userId={} examId={} 无考试记录，返回空 vo", userId, examId);
            return Result.success(vo);
        }

        // [状态机修复] 仅已批阅（status=2）的记录可查看成绩；
        // 进行中（status=0）或已提交待批阅的记录不允许查看成绩
        if (record.getStatus() == null || record.getStatus() != 2) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                    "考试尚未提交，无法查看成绩");
        }

        // 4) 聚合答题详情
        List<ExamAnswer> answers = examAnswerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getRecordId, record.getId())
        );
        long correctCount = 0, wrongCount = 0, unansweredCount = 0;
        if (answers != null) {
            for (ExamAnswer a : answers) {
                if (a.getIsCorrect() != null && a.getIsCorrect() == 1) {
                    correctCount++;
                } else if (a.getIsCorrect() != null && a.getIsCorrect() == 0) {
                    wrongCount++;
                }
                if (a.getStudentAnswer() == null || a.getStudentAnswer().trim().isEmpty()) {
                    unansweredCount++;
                }
            }
        }
        Integer score = record.getScore();
        vo.setScore(score != null ? score : 0);
        vo.setTotalScore(totalScore != null ? totalScore : (score != null ? score : 0));
        vo.setCorrectCount((int) correctCount);
        vo.setWrongCount((int) wrongCount);
        vo.setUnansweredCount((int) unansweredCount);
        // 通过判定：score >= passScore
        vo.setPassed(score != null && passScore != null && score >= passScore);
        // 正确率
        int total = (int) (correctCount + wrongCount + unansweredCount);
        vo.setCorrectRate(total > 0 ? (double) correctCount / total : 0.0);
        return Result.success(vo);
    }
}
