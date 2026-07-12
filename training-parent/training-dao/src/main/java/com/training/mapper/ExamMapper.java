package com.training.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.training.common.entity.Exam;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 考试 Mapper
 */
@Mapper
public interface ExamMapper extends BaseMapper<Exam> {

    /**
     * 分页查询考试列表（支持按课程、标题、类型、状态筛选）
     */
    IPage<Exam> selectExamPage(IPage<Exam> page,
                               @Param("title") String title,
                               @Param("courseId") Long courseId,
                               @Param("examType") Integer examType,
                               @Param("status") Integer status);
}
