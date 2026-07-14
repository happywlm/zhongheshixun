package com.training.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.training.common.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 试题 Mapper
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 分页查询试题列表（支持按课程、标题、类型、难度筛选）
     */
    IPage<Question> selectQuestionPage(IPage<Question> page,
                                       @Param("title") String title,
                                       @Param("courseId") Long courseId,
                                       @Param("questionType") Integer questionType,
                                       @Param("difficulty") Integer difficulty);

    /**
     * 按知识点ID列表查询试题（课程考试严格限定当前课程，避免跨课程混抽）
     */
    List<Question> selectByKnowledgePoints(@Param("courseId") Long courseId,
                                           @Param("kpIds") List<Long> kpIds);

    /**
     * 按知识点ID列表查询试题ID
     */
    List<Long> selectQuestionIdsByKnowledgePoints(@Param("kpIds") List<Long> kpIds);
}
