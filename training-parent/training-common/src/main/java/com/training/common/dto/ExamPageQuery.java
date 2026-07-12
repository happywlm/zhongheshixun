package com.training.common.dto;

import lombok.Data;

/**
 * 考试分页查询
 */
@Data
public class ExamPageQuery extends PageQuery {
    private String title;
    private Long courseId;
    private Integer examType;
    private Integer status;
}
