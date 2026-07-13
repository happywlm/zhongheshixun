package com.training.api.controller;

import com.training.common.dto.WxLoginDTO;
import com.training.common.entity.SysRole;
import com.training.common.entity.SysUser;
import com.training.common.result.Result;
import com.training.common.utils.JwtUtils;
import com.training.common.vo.LoginVO;
import com.training.mapper.SysRoleMapper;
import com.training.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 小程序认证 + 个人中心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class WxAuthController {

    @Resource
    private SysUserService userService;

    @Resource
    private JwtUtils jwtUtils;

    /**
     * 角色字典 Mapper（M13 阶段补 role/roleName 用）
     */
    @Resource
    private SysRoleMapper roleMapper;

    /**
     * BCrypt 密码编码器
     * <p>用途：小程序注册用户时，把默认密码 "123456" 编码为 BCrypt 哈希写入 sys_user.password，
     * 让小程序注册的学员也能用 123456 在 PC 学员端（web-student /admin/login）登录参加考试。</p>
     */
    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * 微信小程序登录
     *
     * 毕设简化版：不实际调用微信服务器获取 openid，
     * 而是用 code 模拟生成 openid，查找或创建学员用户
     */
    @PostMapping("/wx/login")
    public Result<LoginVO> wxLogin(@RequestBody @Valid WxLoginDTO dto) {
        // 1. 模拟 openid（实际应调微信服务器 jscode2session）
        String openid = mockOpenidFromCode(dto.getCode());

        // 2. 根据 openid 查找用户（用户名格式：wx_+openid 前 8 位）
        String username = "wx_" + openid.substring(0, Math.min(8, openid.length()));
        SysUser user = userService.getByUsername(username);

        // 3. 不存在则自动创建学员用户
        if (user == null) {
            user = createStudentUser(username, dto);
            log.info("新用户注册：{}，openid：{}", username, openid);
        }

        // 4. 生成 JWT
        String token = jwtUtils.generate(user.getId(), user.getUsername(), user.getRole());

        // 5. 组装返回
        user.setPassword(null);
        LoginVO vo = new LoginVO();
        vo.setToken(token);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("role", user.getRole());
        userInfo.put("avatar", user.getAvatar());
        vo.setUserInfo(userInfo);

        log.info("小程序用户 {} 登录成功", user.getUsername());
        return Result.success(vo);
    }

    /**
     * 获取当前用户信息（个人中心）
     *
     * <p>M13 阶段修复：补全 {@code role}（角色编码，如 STUDENT/TEACHER/ADMIN）和
     * {@code roleName}（角色显示名，如 学员/讲师/系统管理员），供学员端"角色 badge"展示。</p>
     * <ul>
     *   <li>首选 {@code userService.getUserDetail(id)} —— 内部走 SysUserMapper.selectUserById，
     *       已 LEFT JOIN sys_role 直接拿 role/roleName；</li>
     *   <li>兜底：若 user.roleId 为 null（旧数据）但 user.role 有值，
     *       则用 {@code roleMapper.selectByCode(role)} 反查 sys_role 表。</li>
     * </ul>
     */
    @GetMapping("/user/profile")
    public Result<Map<String, Object>> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        SysUser user = userService.getUserDetail(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        // 1. 解析 roleCode + roleName（兼容旧数据）
        String roleCode = user.getRole();
        String roleName = user.getRoleName();
        if ((roleCode == null || roleCode.isEmpty()) && user.getRoleId() != null) {
            // role 字段未填（极少见，正常 selectUserById 已 JOIN）
            SysRole role = roleMapper.selectById(user.getRoleId());
            if (role != null) {
                roleCode = role.getRoleCode();
                roleName = role.getRoleName();
            }
        } else if (roleCode != null && (roleName == null || roleName.isEmpty())) {
            // 兜底：role 字符串存在但 roleName 没拿到（极少见），按 code 反查
            SysRole role = roleMapper.selectByCode(roleCode.toUpperCase());
            if (role != null) {
                roleName = role.getRoleName();
            }
        }

        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("phone", user.getPhone());
        info.put("email", user.getEmail());
        info.put("role", roleCode);
        info.put("roleName", roleName);
        info.put("avatar", user.getAvatar());
        info.put("orgName", user.getOrgName());
        info.put("jobType", user.getJobType());
        info.put("createTime",
                user.getCreateTime() == null ? null
                        : user.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return Result.success(info);
    }

    /**
     * 更新当前用户信息（昵称、头像、手机、机构、岗位）
     */
    @PutMapping("/user/profile")
    public Result<Map<String, Object>> updateProfile(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        SysUser user = userService.getById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        // 仅更新允许修改的字段
        if (body.containsKey("realName")) user.setRealName((String) body.get("realName"));
        if (body.containsKey("avatar")) user.setAvatar((String) body.get("avatar"));
        if (body.containsKey("phone")) user.setPhone((String) body.get("phone"));
        if (body.containsKey("email")) user.setEmail((String) body.get("email"));
        if (body.containsKey("orgName")) user.setOrgName((String) body.get("orgName"));
        if (body.containsKey("jobType")) user.setJobType((String) body.get("jobType"));
        userService.updateById(user);

        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("realName", user.getRealName());
        info.put("phone", user.getPhone());
        info.put("email", user.getEmail());
        info.put("avatar", user.getAvatar());
        info.put("orgName", user.getOrgName());
        info.put("jobType", user.getJobType());
        return Result.success(info);
    }

    /**
     * 模拟 openid（毕设简化）
     */
    private String mockOpenidFromCode(String code) {
        // 实际应调用微信接口：
        // https://api.weixin.qq.com/sns/jscode2session?appid=APPID&secret=SECRET&js_code=CODE&grant_type=authorization_code
        return UUID.nameUUIDFromBytes(code.getBytes()).toString().replace("-", "").substring(0, 28);
    }

    /**
     * 创建学员用户
     */
    private SysUser createStudentUser(String username, WxLoginDTO dto) {
        SysUser user = new SysUser();
        user.setUsername(username);
        // 默认密码 123456（BCrypt 哈希），让小程序注册的学员也能用 123456
        // 在 PC 学员端（web-student /admin/login）登录参加考试
        user.setPassword(passwordEncoder.encode("123456"));
        user.setRealName(dto.getNickName() != null ? dto.getNickName() : "微信用户");
        user.setAvatar(dto.getAvatarUrl());
        // 修复 Bug #4：角色编码必须大写 STUDENT，与 sys_role 表的 role_code 一致
        // 旧值 "student"（小写）会导致 LEFT JOIN sys_role 查不到记录，
        // /api/user/profile 返回 role=null、roleName=null
        user.setRole("STUDENT");
        // 同时设置 role_id 外键：selectUserById 的 SQL 通过 LEFT JOIN sys_role ON r.id = u.role_id
        // 获取 role_code/role_name，若 role_id 为 null 则 JOIN 结果为 null
        SysRole studentRole = roleMapper.selectByCode("STUDENT");
        if (studentRole != null) {
            user.setRoleId(studentRole.getId());
        }
        user.setStatus(1);
        userService.save(user);
        return user;
    }
}
