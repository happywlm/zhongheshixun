package com.training.common.dto;

import lombok.Data;

/**
 * 试题分页查询
 */
@Data
public class QuestionPageQuery extends PageQuery {
    private String title;
    private Long courseId;
    private Integer questionType;
    private Integer difficulty;
}
