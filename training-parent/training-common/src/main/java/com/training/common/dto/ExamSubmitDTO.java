package com.training.common.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 提交考试请求
 */
@Data
public class ExamSubmitDTO {

    @NotNull(message = "考试ID不能为空")
    private Long examId;

    @NotNull(message = "答案列表不能为空")
    private List<AnswerDTO> answers;

    /** 客户端开考时间戳（毫秒，前端 Date.now()），用于服务端交叉校验防时钟篡改；可空 */
    private Long clientStartTime;

    /** 客户端提交时间戳（毫秒，前端 Date.now()），用于服务端交叉校验；可空 */
    private Long clientEndTime;
}
