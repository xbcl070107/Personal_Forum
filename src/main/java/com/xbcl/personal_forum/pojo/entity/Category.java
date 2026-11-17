package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 板块实体，对应 category 表。
 *
 * <p>板块在本版里是「写死的字典数据」：不做在线增删改，
 * 内容由 sql/init.sql 的 INSERT 灌进去（日常 / 技术 / 水贴）。
 * 所以它这张表里没有 is_deleted，删板块这种事不存在。
 */
@Data
@TableName("category")
public class Category {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 板块名，表上有唯一键 uk_category_name */
    private String name;

    /**
     * 展示顺序，数值小的排前面（日常=1 / 技术=2 / 水贴=3）。
     * 别在 Java 里 sort —— 排序写在 SQL 的 ORDER BY 里，
     * 见 CategoryService.list() 的 orderByAsc。
     */
    private Integer sortOrder;

    /**
     * 这张分类属于哪个业务：'POST' 帖子板块 / 'ITEM' 商品分类。
     *
     * <p>为什么要这一列而不是新建一张 item_category 表：
     * 两种分类的形状完全一样（名字 + 排序），拆表只是让「查分类」这件事
     * 变成两套几乎一样的代码。加了 scope 之后，论坛首页查 POST、
     * 集市的分类 tab 查 ITEM，各拿各的。
     *
     * <p>类型是 String 不是枚举：数据库里存的就是这两个字符串，
     * 用 String 让 MyBatis-Plus 不用配 TypeHandler。取值用下面的常量。
     * 存量 3 个板块由 migration 的 DEFAULT 'POST' 自动归位。
     */
    private String scope;

    /** scope 取值：帖子板块 */
    public static final String SCOPE_POST = "POST";

    /** scope 取值：商品分类 */
    public static final String SCOPE_ITEM = "ITEM";

    /** 创建时间，由数据库默认值填 */
    private LocalDateTime createdAt;
}
