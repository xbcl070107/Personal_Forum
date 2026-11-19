package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.ItemMapper;
import com.xbcl.personal_forum.mapper.ShopMapper;
import com.xbcl.personal_forum.mapper.UserMapper;
import com.xbcl.personal_forum.pojo.dto.ShopCreateDTO;
import com.xbcl.personal_forum.pojo.dto.ShopEditDTO;
import com.xbcl.personal_forum.pojo.entity.Item;
import com.xbcl.personal_forum.pojo.entity.Shop;
import com.xbcl.personal_forum.pojo.entity.User;
import com.xbcl.personal_forum.pojo.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 店铺业务。
 *
 * <p>三条规则决定了这里所有的判断：
 * <ol>
 *   <li><b>一人一店</b>：shop 表 owner_user_id 有唯一键 uk_owner。
 *       Service 先查一次是为了给出人话提示；两个请求同时开店时，
 *       真正拦住第二个的是数据库 —— 撞键抛 DuplicateKeyException，这里翻译成 400</li>
 *   <li><b>店不删只关</b>：status 改 0。原因在 migration-v4-shop.sql 里写着：
 *       软删 + 唯一键会让"关了店想重开"永远失败</li>
 *   <li><b>不是店主的请求一律按"没这家店"处理</b>（404，不是 403）。
 *       403 会告诉对方"这个 id 真的存在，只是不给你"，
 *       等于白送一个店铺 id 的枚举接口</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopMapper shopMapper;

    /** 店铺详情要现数在售商品件数，所以这里也依赖 item 表 */
    private final ItemMapper itemMapper;

    /** W4：店铺主页要显示"店主是谁"，而昵称在 user 表里 —— 这次跨表就靠它 */
    private final UserMapper userMapper;

    /**
     * 开店。
     *
     * <p>@Transactional 在这里其实只有一个写操作，用不上回滚；
     * 留着是因为"先查有没有开过 → 再插"这个读-写之间有时序窗口，
     * 等以后开店要送一条欢迎通知之类的第二条写入时，这个注解不用再想。
     */
    @Transactional(rollbackFor = Exception.class)
    public ShopVO create(Long userId, ShopCreateDTO dto) {

        // 已经开过店就别再开第二家。这里给的是"你已经有店了"，不是"店名重复"
        Shop exist = findByOwner(userId);
        if (exist != null) {
            throw new BusinessException("你已经有店铺了，一人只能开一家");
        }

        Shop shop = new Shop();
        shop.setOwnerUserId(userId);          // 店主只认 token 里的 userId
        shop.setName(dto.getName().trim());
        shop.setAvatarUrl(dto.getAvatarUrl());
        shop.setDescription(dto.getDescription());
        shop.setType(dto.getType() == null ? Shop.TYPE_PERSONAL : dto.getType());
        shop.setStatus(Shop.STATUS_NORMAL);
        // follower_count 在数据库里有 DEFAULT 0，这里显式写 0 是为了让返回的 VO 带上它 ——
        // insert 之后实体不会自动回填数据库默认值
        shop.setFollowerCount(0);

        try {
            shopMapper.insert(shop);
        } catch (DuplicateKeyException e) {
            // 走到这里说明上面那次"有没有开过"的判断被并发穿透了。
            // 唯一键是最后一道闸，翻译成和上面同一句话，前端不用区分两条路径
            throw new BusinessException("你已经有店铺了，一人只能开一家");
        }

        ShopVO vo = ShopVO.of(shop);
        fillOwner(vo, shop.getOwnerUserId());   // 开店响应也带上店主昵称，前端不用再查一次
        return vo;
    }

    /**
     * 改店名 / 头像 / 简介。
     *
     * <p>字段留空 = 不改。全部留空就什么都不做，直接返回现在的样子 ——
     * 空 PUT 当成"读一次"处理，比报"没有字段可改"少一种前端要处理的错。
     */
    @Transactional(rollbackFor = Exception.class)
    public ShopVO update(Long userId, ShopEditDTO dto) {

        Shop shop = requireOwnShop(userId);

        boolean dirty = false;
        if (dto.getName() != null) {
            String name = dto.getName().trim();
            if (name.isEmpty()) {
                throw new BusinessException("店名不能改成空");
            }
            shop.setName(name);
            dirty = true;
        }
        if (dto.getAvatarUrl() != null) {
            shop.setAvatarUrl(dto.getAvatarUrl());
            dirty = true;
        }
        if (dto.getDescription() != null) {
            shop.setDescription(dto.getDescription());
            dirty = true;
        }

        if (dirty) {
            shopMapper.updateById(shop);
        }

        ShopVO vo = ShopVO.of(shop);
        vo.setItemCount(countOnSaleItems(shop.getId()));
        fillOwner(vo, shop.getOwnerUserId());
        return vo;
    }

    /**
     * 我的店（GET /shop/mine）。
     *
     * <p>没开过店返回 null，由 Controller 包成 200 + data:null ——
     * 「还没开店」是正常状态，不是错误，不该让前端按 404 分支处理。
     */
    public ShopVO mine(Long userId) {
        // W4：/shop/* 放进了白名单（店铺主页要公开），代价是 /shop/mine 也会被放进来。
        // 白名单只负责"不拦"，不负责"认人" —— 认人这件事必须在这里补：
        // 没带 token 的请求取出来 userId 是 null，直接当未登录处理。
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        Shop shop = findByOwner(userId);
        if (shop == null) {
            return null;
        }
        ShopVO vo = ShopVO.of(shop);
        vo.setItemCount(countOnSaleItems(shop.getId()));
        fillOwner(vo, shop.getOwnerUserId());
        return vo;
    }

    /**
     * 店铺详情（GET /shop/{id}）。
     *
     * <p>关了的店不返回（404）：店关了就等于不存在，
     * 和"帖子下架"是同一套口径，避免前端到处判断 status。
     */
    public ShopVO detail(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getStatus() == null
                || shop.getStatus() != Shop.STATUS_NORMAL) {
            throw new BusinessException(404, "店铺不存在");
        }
        ShopVO vo = ShopVO.of(shop);
        vo.setItemCount(countOnSaleItems(shop.getId()));
        fillOwner(vo, shop.getOwnerUserId());
        return vo;
    }

    /**
     * 按店主查店。一人一店，所以 selectOne 不会撞多行。
     *
     * <p>这里没有加 status 条件：关了的店也算"开过"，
     * 否则店主关店之后就又能开第二家了。
     */
    public Shop findByOwner(Long userId) {
        return shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getOwnerUserId, userId));
    }

    /**
     * 当前用户的店；没有就 404。
     * 发布商品、改店铺信息都要先过这一关。
     */
    public Shop requireOwnShop(Long userId) {
        Shop shop = findByOwner(userId);
        if (shop == null) {
            throw new BusinessException(404, "你还没有开店");
        }
        return shop;
    }

    /**
     * 补上店主昵称（W4 的「关联查询」）。
     *
     * <p>为什么不写 SQL JOIN：店铺详情只有一行，JOIN 一次也没多贵，
     * 但列表接口（以后可能一次返回 20 家店）用 JOIN 会让每页行数被放大、
     * 还得 distinct 去重；而"主表查出来 → 拿 id 去另一张表批量补名字"
     * 这套写法在列表、详情、缓存场景都能直接套。所以统一走这条。
     *
     * <p>为什么用 displayName() 而不是 getNickname()：
     * nickname 列是 NOT NULL，但历史数据或被手工改过的行可能是空串，
     * 那时页面会显示一片空白 —— displayName() 里写好了 nickname → username 的兜底。
     *
     * <p>为什么判 owner == null：店铺表和用户表之间没有外键（我们全程不加外键），
     * 所以理论上存在"店还在、用户没了"的脏数据。这种情况给空串，
     * 让页面显示"未知店主"，比抛 500 好。
     */
    private void fillOwner(ShopVO vo, Long ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        User owner = userMapper.selectById(ownerUserId);
        vo.setOwnerNickname(owner == null ? "" : owner.displayName());
    }

    /**
     * 在售商品件数。
     *
     * <p>用 selectCount 而不是"把商品查出来数一下"：详情页只要一个数字，
     * 把 20 件商品的标题正文图片全查出来再 length() 是白花钱。
     *
     * <p>只数上架的（status = 1）：下架商品对访客不可见，不该出现在"在售"里。
     */
    private int countOnSaleItems(Long shopId) {
        return Math.toIntExact(itemMapper.selectCount(new LambdaQueryWrapper<Item>()
                .eq(Item::getShopId, shopId)
                .eq(Item::getStatus, Item.STATUS_ON)));
    }
}
