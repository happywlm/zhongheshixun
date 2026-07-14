package com.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.training.common.entity.ConsultKeyword;
import com.training.common.entity.ConsultRecord;
import com.training.common.entity.KnowledgeBase;
import com.training.mapper.ConsultKeywordMapper;
import com.training.mapper.ConsultRecordMapper;
import com.training.mapper.KnowledgeBaseMapper;
import com.training.service.ConsultService;
import com.training.service.ai.LongCatAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 咨询服务实现
 *
 * 优化后的咨询流程（方案 A：AI 优先 + 关键词转人工）：
 * 1. 关键词转人工检查：问题含"转人工/找老师/人工客服"等关键词 → 直接创建人工工单
 * 2. LongCat AI 回答（启用时）：AI 有回复则自动回复
 * 3. 兜底：创建人工工单，等待人工回复
 *
 * 注：知识库 LIKE 匹配已移除（原自动回答"驴头不对马嘴"的根因）
 */
@Slf4j
@Service
public class ConsultServiceImpl implements ConsultService {

    /** 关键词分隔符：中英文逗号、空格、制表符等 */
    private static final Pattern KEYWORD_SPLIT = Pattern.compile("[,，\\s]+");

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private ConsultRecordMapper consultRecordMapper;

    @Resource
    private LongCatAiService longCatAiService;

    @Resource
    private ConsultKeywordMapper consultKeywordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AskResult ask(Long userId, String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("问题不能为空");
        }

        log.info("用户[{}]提问: {}", userId, question);

        // 1. 关键词转人工检查：问题含"转人工/找老师"等关键词 → 直接人工工单
        if (containsTransferHumanKeyword(question)) {
            ConsultRecord record = new ConsultRecord();
            record.setStudentId(userId);
            record.setQuestion(question);
            record.setIsAuto(0);
            consultRecordMapper.insert(record);
            log.info("用户[{}]提问命中转人工关键词, 创建人工工单[id={}]", userId, record.getId());
            return new AskResult(record.getId(), null, false, "human");
        }

        // 2. 调用 LongCat AI（未启用时返回 null，自动走人工兜底）
        String aiAnswer = longCatAiService.ask(question);
        if (StringUtils.hasText(aiAnswer)) {
            ConsultRecord record = new ConsultRecord();
            record.setStudentId(userId);
            record.setQuestion(question);
            record.setAnswer(aiAnswer);
            record.setIsAuto(1);
            record.setReplyTime(LocalDateTime.now());
            consultRecordMapper.insert(record);
            log.info("用户[{}]提问由 LongCat AI 自动回复, consultId={}", userId, record.getId());
            return new AskResult(record.getId(), aiAnswer, true, "ai");
        }

        // 3. 兜底：创建人工工单
        ConsultRecord record = new ConsultRecord();
        record.setStudentId(userId);
        record.setQuestion(question);
        record.setIsAuto(0);
        consultRecordMapper.insert(record);
        log.info("用户[{}]提问创建人工工单[id={}]", userId, record.getId());
        return new AskResult(record.getId(), null, false, "human");
    }

    /**
     * 检查问题中是否包含转人工关键词
     */
    private boolean containsTransferHumanKeyword(String question) {
        List<ConsultKeyword> keywords = consultKeywordMapper.selectList(
                new LambdaQueryWrapper<ConsultKeyword>()
                        .eq(ConsultKeyword::getAction, "to_human")
                        .eq(ConsultKeyword::getEnabled, 1)
                        .orderByAsc(ConsultKeyword::getSortOrder)
        );
        for (ConsultKeyword kw : keywords) {
            if (question.contains(kw.getKeyword())) {
                log.info("问题命中转人工关键词：{}", kw.getKeyword());
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long consultId, String answer) {
        if (consultId == null) {
            throw new IllegalArgumentException("咨询ID不能为空");
        }
        if (!StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("回复内容不能为空");
        }
        ConsultRecord exist = consultRecordMapper.selectById(consultId);
        if (exist == null) {
            throw new IllegalArgumentException("咨询记录不存在");
        }
        int rows = consultRecordMapper.updateReply(consultId, answer, 2, LocalDateTime.now());
        if (rows <= 0) {
            throw new IllegalStateException("回复失败，请重试");
        }
        log.info("管理员回复咨询[id={}]", consultId);
    }

    @Override
    public List<ConsultRecord> getOverdueConsults(int slaHours) {
        if (slaHours <= 0) {
            slaHours = 24;
        }
        return consultRecordMapper.selectOverdue(slaHours);
    }

    @Override
    public IPage<ConsultRecord> page(String keyword, Integer isAuto, int pageNum, int pageSize) {
        Page<ConsultRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ConsultRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ConsultRecord::getQuestion, keyword);
        }
        if (isAuto != null) {
            wrapper.eq(ConsultRecord::getIsAuto, isAuto);
        }
        // 未回复的排在前面（便于管理员优先处理）
        wrapper.orderByAsc(ConsultRecord::getReplyTime);
        wrapper.orderByDesc(ConsultRecord::getCreateTime);
        return consultRecordMapper.selectPage(page, wrapper);
    }

    @Override
    public IPage<ConsultRecord> myList(Long studentId, int pageNum, int pageSize) {
        Page<ConsultRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ConsultRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultRecord::getStudentId, studentId);
        wrapper.orderByDesc(ConsultRecord::getCreateTime);
        return consultRecordMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferHuman(Long consultId, Long userId) {
        if (consultId == null) {
            throw new IllegalArgumentException("咨询ID不能为空");
        }
        ConsultRecord exist = consultRecordMapper.selectById(consultId);
        if (exist == null) {
            throw new IllegalArgumentException("咨询记录不存在");
        }
        // [水平越权修复 / #3 评审项] 仅允许本人对自己的咨询发起转人工，
        // 防止恶意用户通过 consultId 清空他人 AI 回复。
        if (userId == null || !userId.equals(exist.getStudentId())) {
            throw new IllegalArgumentException("无权操作他人咨询记录");
        }
        // 使用 LambdaUpdateWrapper 显式设置 answer/reply_time 为 null
        // （MyBatis-Plus updateById 默认不更新 null 字段）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConsultRecord> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConsultRecord>()
                        .eq(ConsultRecord::getId, consultId)
                        .set(ConsultRecord::getAnswer, null)
                        .set(ConsultRecord::getIsAuto, 0)
                        .set(ConsultRecord::getReplyTime, null);
        consultRecordMapper.update(null, wrapper);
        log.info("学员主动转人工: consultId={}, userId={}", consultId, userId);
    }

    /**
     * 从问题中提取关键词列表
     * 策略：
     * 1. 先按中英文逗号、空格等分隔符拆分
     * 2. 如果只有一个长字符串（中文常见），则同时提取 2-4 字的滑动窗口子串
     *    这样 "考试没通过可以重考吗" 会生成 "考试","试没","没通"... "重考" 等子串
     * 3. 同时保留整词作为 keyword（用于 question LIKE 匹配）
     */
    private List<String> extractKeywords(String question) {
        if (!StringUtils.hasText(question)) {
            return java.util.Collections.emptyList();
        }

        Set<String> result = new LinkedHashSet<>();
        String trimmed = question.trim();

        // 1. 按分隔符拆分
        String[] parts = KEYWORD_SPLIT.split(trimmed);

        for (String part : parts) {
            if (!StringUtils.hasText(part)) continue;
            part = part.trim();
            result.add(part);

            // 2. 如果是中文长字符串（>= 4 字），生成 2-3 字滑动窗口
            if (part.length() >= 4) {
                for (int len = 2; len <= 3; len++) {
                    for (int i = 0; i + len <= part.length(); i++) {
                        result.add(part.substring(i, i + len));
                    }
                }
            }
        }

        return new ArrayList<>(result);
    }
}