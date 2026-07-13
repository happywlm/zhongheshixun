package com.training.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.training.common.dto.CoursePageQuery;
import com.training.common.dto.StudyProgressDTO;
import com.training.common.entity.Course;
import com.training.common.entity.CourseEnroll;
import com.training.common.entity.StudyRecord;
import com.training.common.vo.StudyProgressVO;

import java.util.List;

/**
 * 学习服务接口
 */
public interface StudyService extends IService<StudyRecord> {

    /**
     * 上报/更新学习进度（累加时长）
     */
    void reportProgress(Long userId, StudyProgressDTO dto);

    /**
     * 查询某课程所有章节的学习进度
     */
    List<StudyProgressVO> getProgress(Long userId, Long courseId);

    /**
     * 报名课程（已报名则抛 ENROLL_EXISTS）
     */
    void enroll(Long userId, Long courseId);

    /**
     * 查询学员已报名的课程列表
     */
    IPage<Course> myCourses(Long userId, CoursePageQuery query);

    /**
     * 检查学员是否已报名某课程（轻量查询，避免拉取整页 my-courses）
     * 修复 #8：旧前端为判断 1 个 courseId 是否报名，需请求 my-courses?pageSize=100
     */
    boolean isEnrolled(Long userId, Long courseId);
}
