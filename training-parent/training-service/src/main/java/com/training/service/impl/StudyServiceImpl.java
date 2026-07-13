package com.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.training.common.dto.CoursePageQuery;
import com.training.common.dto.StudyProgressDTO;
import com.training.common.entity.Course;
import com.training.common.entity.CourseEnroll;
import com.training.common.entity.StudyRecord;
import com.training.common.result.ResultCode;
import com.training.common.exception.BusinessException;
import com.training.common.vo.StudyProgressVO;
import com.training.mapper.CourseEnrollMapper;
import com.training.mapper.CourseMapper;
import com.training.mapper.StudyRecordMapper;
import com.training.service.StudyService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学习服务实现
 */
@Service
public class StudyServiceImpl extends ServiceImpl<StudyRecordMapper, StudyRecord> implements StudyService {

    @Resource
    private CourseEnrollMapper courseEnrollMapper;

    @Resource
    private CourseMapper courseMapper;

    @Override
    public void reportProgress(Long userId, StudyProgressDTO dto) {
        StudyRecord record = new StudyRecord();
        record.setStudentId(userId);
        record.setCourseId(dto.getCourseId());
        record.setChapterId(dto.getChapterId());
        record.setProgress(dto.getProgress() == null ? 0 : dto.getProgress());
        record.setStudyDuration(dto.getStudyDuration() == null ? 0 : dto.getStudyDuration());
        record.setLastPosition(dto.getLastPosition() == null ? 0 : dto.getLastPosition());
        // completed: Boolean → 0/1
        boolean completed = dto.getCompleted() != null && dto.getCompleted();
        record.setCompleted(completed ? 1 : 0);
        // 进度 100 时强制 completed=1
        if (record.getProgress() != null && record.getProgress() >= 100) {
            record.setProgress(100);
            record.setCompleted(1);
        }
        baseMapper.upsertProgress(record);
    }

    @Override
    public List<StudyProgressVO> getProgress(Long userId, Long courseId) {
        LambdaQueryWrapper<StudyRecord> wrapper = new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getStudentId, userId)
                .eq(StudyRecord::getCourseId, courseId);
        List<StudyRecord> list = baseMapper.selectList(wrapper);
        return list.stream().map(r -> {
            StudyProgressVO vo = new StudyProgressVO();
            vo.setChapterId(r.getChapterId());
            vo.setProgress(r.getProgress());
            vo.setStudyDuration(r.getStudyDuration());
            vo.setLastPosition(r.getLastPosition());
            vo.setCompleted(r.getCompleted());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void enroll(Long userId, Long courseId) {
        // 检查是否已报名
        LambdaQueryWrapper<CourseEnroll> wrapper = new LambdaQueryWrapper<CourseEnroll>()
                .eq(CourseEnroll::getStudentId, userId)
                .eq(CourseEnroll::getCourseId, courseId);
        CourseEnroll exist = courseEnrollMapper.selectOne(wrapper);
        if (exist != null) {
            throw new BusinessException(ResultCode.ENROLL_EXISTS);
        }
        CourseEnroll enroll = new CourseEnroll();
        enroll.setStudentId(userId);
        enroll.setCourseId(courseId);
        courseEnrollMapper.insert(enroll);
    }

    @Override
    public IPage<Course> myCourses(Long userId, CoursePageQuery query) {
        Page<Course> page = new Page<>(query.getPageNum(), query.getPageSize());
        return courseMapper.selectEnrolledCourses(page, userId);
    }

    @Override
    public boolean isEnrolled(Long userId, Long courseId) {
        // 修复 #8：轻量查询，count 1 条记录即可，避免拉取整页 my-courses
        LambdaQueryWrapper<CourseEnroll> wrapper = new LambdaQueryWrapper<CourseEnroll>()
                .eq(CourseEnroll::getStudentId, userId)
                .eq(CourseEnroll::getCourseId, courseId);
        Long count = courseEnrollMapper.selectCount(wrapper);
        return count != null && count > 0;
    }
}
