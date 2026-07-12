package com.training.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code role} / {@code roleName} 来自 sys_role 表，{@link TableField#exist()} = false，
 *       不参与 MyBatis-Plus 自动 SQL 生成（避免 {@code Unknown column 'role'}）。</li>
 *   <li>{@code roleId} 持久化字段，关联 sys_role.id。</li>
 * </ul>
 * </p>
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码（bcrypt加密）*/
    private String password;

    /** 姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 角色编码（admin/teacher/student），由关联查询填充，非持久化字段 */
    @TableField(exist = false)
    private String role;

    /** 角色ID，FK -> sys_role.id */
    private Long roleId;

    /** 角色显示名（系统管理员/讲师/学员），由关联查询填充，非持久化字段 */
    @TableField(exist = false)
    private String roleName;

    /** 头像URL */
    private String avatar;

    /** 所属机构 */
    private String orgName;

    /** 岗位类型：临床/公卫/护理/医技 */
    private String jobType;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0正常 1已删（与数据库层其他实体对齐，使 MyBatis-Plus 自动拼接 deleted=0）*/
    @TableLogic
    private Integer deleted;
}
