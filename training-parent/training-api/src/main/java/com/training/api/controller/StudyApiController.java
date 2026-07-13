package com.training.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.training.common.dto.CoursePageQuery;
import com.training.common.dto.EnrollDTO;
import com.training.common.dto.StudyProgressDTO;
import com.training.common.entity.Course;
import com.training.common.result.PageResult;
import com.training.common.result.Result;
import com.training.common.vo.StudyProgressVO;
import com.training.service.StudyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 小程序学习接口（需要登录）
 */
@RestController
@RequestMapping("/api/study")
public class StudyApiController {

    @Resource
    private StudyService studyService;

    /**
     * 上报学习进度
     */
    @PostMapping("/progress")
    public Result<Void> reportProgress(@RequestBody @Valid StudyProgressDTO dto,
                                       @RequestAttribute("userId") Long userId) {
        studyService.reportProgress(userId, dto);
        return Result.success();
    }

    /**
     * 查询某课程的学习进度
     */
    @GetMapping("/progress/{courseId}")
    public Result<List<StudyProgressVO>> getProgress(@PathVariable Long courseId,
                                                     @RequestAttribute("userId") Long userId) {
        List<StudyProgressVO> list = studyService.getProgress(userId, courseId);
        return Result.success(list);
    }

    /**
     * 报名课程
     */
    @PostMapping("/enroll")
    public Result<Void> enroll(@RequestBody @Valid EnrollDTO dto,
                               @RequestAttribute("userId") Long userId) {
        studyService.enroll(userId, dto.getCourseId());
        return Result.success();
    }

    /**
     * 我的课程（已报名）
     */
    @GetMapping("/my-courses")
    public Result<PageResult<Course>> myCourses(@RequestAttribute("userId") Long userId,
                                                CoursePageQuery q) {
        IPage<Course> page = studyService.myCourses(userId, q);
        long total = page.getTotal();
        // 修复分页不设置 total 的兜底
        if (total <= 0 && page.getRecords() != null && !page.getRecords().isEmpty()) {
            total = page.getRecords().size();
        }
        return Result.success(PageResult.of(page.getRecords(), total,
                (int) page.getCurrent(), (int) page.getSize()));
    }

    /**
     * 检查是否已报名某课程（轻量接口）
     * 修复 #8：前端 study.js getCourseDetail 原需请求 my-courses?pageSize=100 判断 1 个 courseId
     */
    @GetMapping("/check-enrolled")
    public Result<Boolean> checkEnrolled(@RequestAttribute("userId") Long userId,
                                         @RequestParam("courseId") Long courseId) {
        return Result.success(studyService.isEnrolled(userId, courseId));
    }
}
