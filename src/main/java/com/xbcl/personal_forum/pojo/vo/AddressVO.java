package com.xbcl.personal_forum.pojo.vo;

import com.xbcl.personal_forum.pojo.entity.Address;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条收货地址，给前端看的形状（W5）。
 *
 * <p>比实体少两个字段：
 * <ul>
 *   <li>没有 userId —— 调用者只会拿到自己的地址，回 userId 是多余信息
 *       （也顺手少了一个「前端拿 userId 去干什么」的机会）。</li>
 *   <li>没有 isDeleted —— 逻辑删除的行根本查不出来（MyBatis-Plus 自动补 is_deleted = 0）。</li>
 * </ul>
 */
@Data
public class AddressVO {

    private Long id;

    private String receiverName;

    private String phone;

    private String region;

    private String detail;

    /** 1 = 默认地址，0 = 普通地址。前端用它决定 picker 里打不打勾 */
    private Integer isDefault;

    private LocalDateTime createdAt;

    public static AddressVO of(Address address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setReceiverName(address.getReceiverName());
        vo.setPhone(address.getPhone());
        vo.setRegion(address.getRegion());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        vo.setCreatedAt(address.getCreatedAt());
        return vo;
    }
}
