package com.training.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.training.common.dto.UserForm;
import com.training.common.dto.UserPageQuery;
import com.training.common.entity.SysRole;
import com.training.common.entity.SysUser;
import com.training.common.entity.Teacher;
import com.training.mapper.SysRoleMapper;
import com.training.mapper.SysUserMapper;
import com.training.mapper.TeacherMapper;
import com.training.service.SysUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * 用户服务实现
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private TeacherMapper teacherMapper;

    /**
     * 根据角色编码（小写/大写均可）查询 sys_role.id
     * @param roleCode 角色编码，如 admin/ADMIN/teacher/TEACHER
     * @return 角色ID，未找到返回 null
     */
    private Long resolveRoleId(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return null;
        }
        // 统一转大写查 sys_role（表中 role_code 为 ADMIN/TEACHER/STUDENT）
        SysRole role = sysRoleMapper.selectByCode(roleCode.toUpperCase());
        return role != null ? role.getId() : null;
    }

    /**
     * 为讲师角色用户自动创建 teacher 表关联记录
     * @param userId 用户ID
     * @param realName 讲师姓名
     * @param role 角色编码
     */
    private void syncTeacherRecord(Long userId, String realName, String role) {
        if (!"teacher".equalsIgnoreCase(role)) {
            return;
        }
        // 检查是否已存在 teacher 记录（避免重复创建）
        Teacher exist = teacherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Teacher>()
                        .eq(Teacher::getUserId, userId)
        );
        if (exist != null) {
            return;
        }
        Teacher teacher = new Teacher();
        teacher.setUserId(userId);
        teacher.setRealName(realName);
        teacherMapper.insert(teacher);
    }

    @Override
    public SysUser getByUsername(String username) {
        return baseMapper.selectByUsername(username);
    }

    @Override
    public SysUser getUserDetail(Long id) {
        return baseMapper.selectUserById(id);
    }

    @Override
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public IPage<SysUser> page(UserPageQuery query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        return baseMapper.selectUserPage(page, query.getRole(), query.getStatus(), query.getKeyword());
    }

    @Override
    public boolean createUser(UserForm form) {
        // 校验用户名唯一
        SysUser exist = getByUsername(form.getUsername());
        if (exist != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(form.getUsername());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRealName(form.getRealName());
        user.setPhone(form.getPhone());
        user.setEmail(form.getEmail());
        // 关键修复：role 是非持久化字段，必须设置 roleId 才能写入数据库
        // 根据前端传入的 role 字符串（admin/teacher/student）查 sys_role 表获取 role_id
        Long roleId = resolveRoleId(form.getRole());
        if (roleId == null) {
            throw new IllegalArgumentException("无效的角色编码：" + form.getRole());
        }
        user.setRoleId(roleId);
        user.setAvatar(form.getAvatar());
        user.setOrgName(form.getOrgName());
        user.setJobType(form.getJobType());
        user.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        boolean ok = save(user);
        // 讲师联动：角色为 teacher 时自动创建 teacher 表关联记录
        if (ok) {
            syncTeacherRecord(user.getId(), form.getRealName(), form.getRole());
        }
        return ok;
    }

    @Override
    public boolean updateUser(UserForm form) {
        if (form.getId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        SysUser exist = getById(form.getId());
        if (exist == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        // 用户名唯一校验（排除自己）
        if (StringUtils.hasText(form.getUsername())) {
            SysUser sameName = getByUsername(form.getUsername());
            if (sameName != null && !sameName.getId().equals(form.getId())) {
                throw new IllegalArgumentException("用户名已存在");
            }
            exist.setUsername(form.getUsername());
        }
        if (StringUtils.hasText(form.getPassword())) {
            exist.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        if (form.getRealName() != null) {
            exist.setRealName(form.getRealName());
        }
        if (form.getPhone() != null) {
            exist.setPhone(form.getPhone());
        }
        if (form.getEmail() != null) {
            exist.setEmail(form.getEmail());
        }
        if (form.getRole() != null) {
            // 关键修复：更新角色时必须同步更新 roleId
            Long roleId = resolveRoleId(form.getRole());
            if (roleId == null) {
                throw new IllegalArgumentException("无效的角色编码：" + form.getRole());
            }
            exist.setRoleId(roleId);
            // 讲师联动：角色切换为 teacher 时自动创建 teacher 表关联记录
            syncTeacherRecord(exist.getId(), form.getRealName(), form.getRole());
        }
        if (form.getAvatar() != null) {
            exist.setAvatar(form.getAvatar());
        }
        if (form.getOrgName() != null) {
            exist.setOrgName(form.getOrgName());
        }
        if (form.getJobType() != null) {
            exist.setJobType(form.getJobType());
        }
        if (form.getStatus() != null) {
            exist.setStatus(form.getStatus());
        }
        return updateById(exist);
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        SysUser user = getById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setStatus(status);
        return updateById(user);
    }
}
