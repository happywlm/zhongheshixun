package com.training.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.training.common.entity.Teacher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 讲师 Mapper
 */
@Mapper
public interface TeacherMapper extends BaseMapper<Teacher> {

    /**
     * 分页查询讲师列表（支持姓名、教学方向筛选）
     */
    IPage<Teacher> selectTeacherPage(IPage<Teacher> page,
                                     @Param("realName") String realName,
                                     @Param("direction") String direction,
                                     @Param("keyword") String keyword);
}
