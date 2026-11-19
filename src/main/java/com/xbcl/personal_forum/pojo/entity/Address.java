package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址实体，对应 address 表。
 *
 * <p>W5（下单）才会写 Service，现在只有表 / 实体 / Mapper。
 *
 * <p>注意 is_default <b>没有唯一键</b>：软删的表上再挂业务唯一键，
 * 「删掉的默认地址」会永远占着那个坑。设默认地址时用事务保证
 * 「同一用户最多一条 is_default=1」：先全部置 0，再置 1。
 */
@Data
@TableName("address")
public class Address {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String receiverName;

    private String phone;

    /** 省市区，例如「浙江省 杭州市 西湖区」 */
    private String region;

    /** 详细地址（门牌号） */
    private String detail;

    /** 1 = 默认地址 */
    private Integer isDefault;

    private LocalDateTime createdAt;

    private Integer isDeleted;
}
