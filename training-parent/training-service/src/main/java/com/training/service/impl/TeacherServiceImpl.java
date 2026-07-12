package com.training.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.training.common.dto.TeacherPageQuery;
import com.training.common.entity.Teacher;
import com.training.mapper.TeacherMapper;
import com.training.service.TeacherService;
import org.springframework.stereotype.Service;

/**
 * 讲师服务实现
 */
@Service
public class TeacherServiceImpl extends ServiceImpl<TeacherMapper, Teacher> implements TeacherService {

    @Override
    public IPage<Teacher> page(TeacherPageQuery query) {
        Page<Teacher> page = new Page<>(query.getPageNum(), query.getPageSize());
        return baseMapper.selectTeacherPage(page, query.getRealName(), query.getDirection(), query.getKeyword());
    }

    @Override
    public boolean create(Teacher teacher) {
        // 防御：user_id 可空（独立维护讲师档案场景）
        return save(teacher);
    }

    @Override
    public boolean updateTeacher(Teacher teacher) {
        if (teacher.getId() == null) {
            throw new IllegalArgumentException("讲师ID不能为空");
        }
        Teacher exist = getById(teacher.getId());
        if (exist == null) {
            throw new IllegalArgumentException("讲师不存在");
        }
        return updateById(teacher);
    }
}
