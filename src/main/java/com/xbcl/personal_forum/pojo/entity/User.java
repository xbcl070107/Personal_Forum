package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库里的 user 表（见 sql/init.sql）。
 *
 * <p>@Data 是 Lombok 注解，编译期生成 getter / setter / toString / equals / hashCode。
 * 所以这个文件里看不到 getUsername()，但它确实存在。
 *
 * <p>@TableName("user") 声明对应哪张表。类名首字母大写、表名全小写，
 * 对不上就必须显式写，不写 MyBatis-Plus 会去找一张叫 "User" 的表。
 *
 * <p>字段名到列名的转换靠 application.yaml 里的 map-underscore-to-camel-case: true：
 * passwordHash → password_hash，createdAt → created_at。那一行别关。
 */
@Data
@TableName("user")
public class User {

    /**
     * 主键。
     * IdType.AUTO = 交给数据库的 AUTO_INCREMENT 生成，
     * insert 之后 MyBatis-Plus 会把新 id 回填到这个对象上。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名。表上有唯一键 uk_user_username，不允许重复 */
    private String username;

    /**
     * 昵称。给别人看的名，<b>允许重复</b> —— 表上没有唯一键，这是需求不是漏了。
     *
     * <p>和 username 的分工：
     * <ul>
     *   <li>username 是登录凭证，唯一，不显示给别人看</li>
     *   <li>nickname 是展示名，页面上凡是「这个人叫什么」的地方都用它</li>
     * </ul>
     * 合在一起就会出现「我想叫提灯人，但已经有人叫了」这种死路。
     */
    private String nickname;

    /**
     * 页面上该显示什么名字。
     *
     * <p>兜底顺序是 nickname → username：正常情况 nickname 一定有值
     * （列是 NOT NULL、注册时必填），所以这行主要是防止历史数据或
     * 以后某个只填了 username 的写入路径，让页面出现一片空白。
     *
     * <p>放在实体上而不是各个 VO 里各写一遍 —— PostVO 和 CommentVO
     * 都要用同一套规则，两处各写一份，将来改兜底顺序就会漏一处。
     */
    public String displayName() {
        return (nickname == null || nickname.isBlank()) ? username : nickname;
    }

    /**
     * 密码的 BCrypt 哈希（一串 60 个字符的乱码），不是明文，也解不回明文。
     * 任何接口都别把它返回给前端 —— 前端要的只是 id / username / role。
     */
    private String passwordHash;

    /** 角色：ADMIN / USER。目前注册一律给 USER，管理员靠 SQL 手动改 */
    private String role;

    /** 注册时间，由数据库的 DEFAULT CURRENT_TIMESTAMP 填，Java 这边不设置它 */
    private LocalDateTime createdAt;
}
