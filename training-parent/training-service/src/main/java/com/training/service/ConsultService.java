package com.training.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.training.common.entity.ConsultRecord;

import java.util.List;

/**
 * 咨询服务接口
 */
public interface ConsultService {

    /**
     * 咨询结果
     */
    class AskResult {
        private Long consultId;
        private String autoReply;
        private Boolean matched;
        /** 来源：kb=知识库, ai=LongCat AI, human=人工 */
        private String source;

        public AskResult() {}

        public AskResult(Long consultId, String autoReply, Boolean matched, String source) {
            this.consultId = consultId;
            this.autoReply = autoReply;
            this.matched = matched;
            this.source = source;
        }

        public Long getConsultId() { return consultId; }
        public void setConsultId(Long consultId) { this.consultId = consultId; }
        public String getAutoReply() { return autoReply; }
        public void setAutoReply(String autoReply) { this.autoReply = autoReply; }
        public Boolean getMatched() { return matched; }
        public void setMatched(Boolean matched) { this.matched = matched; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    /**
     * 发起咨询（先关键词匹配知识库，未命中则调用 LongCat AI，均未响应则创建人工工单）
     *
     * @param userId   学员ID
     * @param question 问题内容
     * @return AskResult，包含 consultId、autoReply、matched、source
     */
    AskResult ask(Long userId, String question);

    /**
     * 人工回复
     *
     * @param consultId 咨询记录ID
     * @param answer    回复内容
     */
    void reply(Long consultId, String answer);

    /**
     * SLA 超时告警：未回复超过 slaHours（默认24小时）的工单
     *
     * @param slaHours 超时阈值（小时）
     * @return 超时工单列表
     */
    List<ConsultRecord> getOverdueConsults(int slaHours);

    /**
     * 分页查询咨询记录
     */
    IPage<ConsultRecord> page(String keyword, Integer isAuto, int pageNum, int pageSize);

    /**
     * 学员查询自己的咨询列表
     */
    IPage<ConsultRecord> myList(Long studentId, int pageNum, int pageSize);

    /**
     * 学员主动转人工：将已自动回复的咨询转为人工工单等待人工回复
     *
     * @param consultId 咨询记录ID
     * @param userId    当前登录学员ID（用于越权校验，仅允许本人转人工自己的咨询）
     */
    void transferHuman(Long consultId, Long userId);
}