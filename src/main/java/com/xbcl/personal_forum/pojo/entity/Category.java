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

    /** 创建时间，由数据库默认值填 */
    private LocalDateTime createdAt;
}
