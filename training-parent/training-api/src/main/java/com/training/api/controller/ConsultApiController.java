package com.training.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.training.common.entity.ConsultRecord;
import com.training.common.result.PageResult;
import com.training.common.result.Result;
import com.training.service.ConsultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 小程序咨询接口（JWT 登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/consult")
public class ConsultApiController {

    @Resource
    private ConsultService consultService;

    /**
     * 发起咨询（body: { question }）→ { consultId, autoReply, matched }
     */
    @PostMapping("/ask")
    public Result<ConsultService.AskResult> ask(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String question = (String) body.get("question");
        if (question == null || question.trim().isEmpty()) {
            return Result.error(400, "问题不能为空");
        }
        ConsultService.AskResult result = consultService.ask(userId, question);
        return Result.success(result);
    }

    /**
     * 我的咨询列表
     */
    @GetMapping("/my")
    public Result<PageResult<ConsultRecord>> myList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        IPage<ConsultRecord> page = consultService.myList(userId, pageNum, pageSize);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /**
     * 学员主动转人工：将已自动回复的咨询转为人工工单
     * body: { consultId }
     */
    @PostMapping("/transfer-human")
    public Result<Void> transferHuman(@RequestBody Map<String, Object> body,
                                       HttpServletRequest request) {
        Object idObj = body.get("consultId");
        if (idObj == null) {
            return Result.error(400, "consultId 不能为空");
        }
        Long consultId;
        try {
            consultId = Long.parseLong(String.valueOf(idObj));
        } catch (NumberFormatException e) {
            return Result.error(400, "consultId 格式错误");
        }
        // [水平越权修复 / #3 评审项] 从 request 取当前登录学员ID，service 层校验 consult_record.student_id == userId
        Long userId = (Long) request.getAttribute("userId");
        consultService.transferHuman(consultId, userId);
        return Result.success(null);
    }
}
