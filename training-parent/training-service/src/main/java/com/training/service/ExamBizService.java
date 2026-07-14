package com.training.service;

import com.training.common.dto.AnswerDTO;
import com.training.common.result.PageResult;
import com.training.common.vo.ExamListVO;
import com.training.common.vo.ExamResultVO;
import com.training.common.vo.ExamStartVO;
import com.training.common.vo.GenerateResultVO;

import java.util.List;

/**
 * 考试业务服务接口（组卷、开始、提交、阅卷）
 */
public interface ExamBizService {

    /**
     * 学员维度考试列表（含学员维度状态、剩余重考次数）— P1-7 修复：支持分页
     *
     * @param userId   当前学员ID
     * @param status   学员维度状态筛选：0未开始 1已完成(已提交) 2已批阅；null 表示全部
     * @param pageNum  页码（1-based），null/<=0 时按 1 处理
     * @param pageSize 每页条数，null/<=0 时按 9 处理（与前端默认一致）
     * @return 学员维度考试分页结果，按 exam.createTime DESC 排序
     */
    PageResult<ExamListVO> listForStudent(Long userId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 自动组卷：按难度比例 30:50:20 从指定知识点的题库中随机抽题
     *
     * @return 试题ID列表
     */
    List<Long> autoGeneratePaper(Long examId, List<Long> knowledgePointIds);

    /**
     * 开始考试：校验时间窗口 + 重考次数，生成/复用试卷，创建考试记录
     */
    ExamStartVO startExam(Long examId, Long studentId);

    /**
     * 提交考试 + 自动阅卷
     *
     * @param examId          考试ID
     * @param studentId       学员ID
     * @param answers         答案列表
     * @param clientStartTime 客户端开考时间戳（毫秒，由前端 Date.now() 上报），用于交叉校验防时钟篡改；可空
     * @param clientEndTime   客户端提交时间戳（毫秒），用于交叉校验；可空
     */
    ExamResultVO submitExam(Long examId, Long studentId, List<AnswerDTO> answers,
                            Long clientStartTime, Long clientEndTime);

    /**
     * 管理员自动组卷并持久化模板试卷（studentId = 0L 作为模板标识）
     *
     * @param examId            考试ID
     * @param knowledgePointIds 知识点ID列表
     * @param adminId           操作管理员ID（仅记录，不影响模板唯一性）
     * @return 抽中的题目ID列表 + 总数
     */
    GenerateResultVO generateAndSavePaper(Long examId, List<Long> knowledgePointIds, Long adminId);
}
