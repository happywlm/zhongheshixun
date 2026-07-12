package com.training.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试题实体
 */
@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联课程ID */
    private Long courseId;

    /** 关联知识点ID */
    private Long knowledgePointId;

    /** 题目 */
    private String title;

    /** 类型：1单选 2多选 3判断 4填空 5问答 */
    private Integer questionType;

    /** 选项(JSON) */
    private String options;

    /** 正确答案 */
    private String answer;

    /** 分值 */
    private Integer score;

    /** 难度：1简单 2普通 3困难 */
    private Integer difficulty;

    /** 答案解析 */
    private String analysis;

    private LocalDateTime createTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
