package com.training.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.training.common.dto.QuestionPageQuery;
import com.training.common.entity.Question;
import com.training.mapper.QuestionMapper;
import com.training.service.QuestionService;
import org.springframework.stereotype.Service;

/**
 * 试题服务实现
 */
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Override
    public IPage<Question> page(QuestionPageQuery query) {
        Page<Question> page = new Page<>(query.getPageNum(), query.getPageSize());
        return baseMapper.selectQuestionPage(page, query.getTitle(), query.getCourseId(), query.getQuestionType(), query.getDifficulty());
    }

    @Override
    public boolean create(Question question) {
        if (question.getScore() == null) {
            question.setScore(1);
        }
        if (question.getDifficulty() == null) {
            question.setDifficulty(2);
        }
        // 防御：question_type 前端未传时默认 1 (单选)
        if (question.getQuestionType() == null) {
            question.setQuestionType(1);
        }
        return save(question);
    }

    @Override
    public boolean updateQuestion(Question question) {
        if (question.getId() == null) {
            throw new IllegalArgumentException("试题ID不能为空");
        }
        Question exist = getById(question.getId());
        if (exist == null) {
            throw new IllegalArgumentException("试题不存在");
        }
        return updateById(question);
    }
}
