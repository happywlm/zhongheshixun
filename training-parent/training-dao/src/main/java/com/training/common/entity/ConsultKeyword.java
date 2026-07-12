package com.training.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 咨询关键词路由配置实体
 *
 * <p>用于配置学员提问中包含特定关键词时的路由动作：
 * <ul>
 *   <li>to_human: 直接转人工工单</li>
 *   <li>to_ai: 转由 AI 回答（预留）</li>
 * </ul>
 * </p>
 */
@Data
@TableName("consult_keyword")
public class ConsultKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发关键词 */
    private String keyword;

    /** 动作：to_human=转人工 / to_ai=转AI */
    private String action;

    /** 排序（小的优先） */
    private Integer sortOrder;

    /** 是否启用：0禁用 1启用 */
    private Integer enabled;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
