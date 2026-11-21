package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.AddressMapper;
import com.xbcl.personal_forum.pojo.dto.AddressSaveDTO;
import com.xbcl.personal_forum.pojo.entity.Address;
import com.xbcl.personal_forum.pojo.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 收货地址业务（W5 上半场：地址管理）。
 *
 * <p>这块本身不难，难的是「一个人可以有多条地址」带来的两个细节：
 * <ol>
 *   <li><b>默认地址只能有一条。</b>但 address 表上<b>没有</b>部分唯一索引
 *       （ONLY is_default=1 那种在逻辑删除下会误伤，见 Address 实体注释），
 *       所以「先全清 0、再置 1」必须在同一个事务里做完，否则中间那个
 *       「一条默认都没有」的状态会被别的请求读到。</li>
 *   <li><b>删掉默认地址之后不能没人当默认。</b>要么立刻把剩下的一条提上来，
 *       要么下单页允许「没选地址也能下单」—— 后者显然不行。</li>
 * </ol>
 *
 * <p>所有方法第一个参数都是 userId：地址是私有数据，别人地址一律按「不存在」回 404。
 * 不要回 403 —— 403 等于告诉对方「这个 id 是存在的」。
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    /** 一个账号最多存几条。20 够用，也顺手避免有人拿这个接口往表里灌数据 */
    private static final int MAX_ADDRESS = 20;

    private final AddressMapper addressMapper;

    /**
     * 我的地址列表：默认地址排最前，其余按新→旧。
     *
     * <pre>
     * 步骤 1（只有 3 行，别想复杂了）：
     *   List&lt;Address&gt; list = addressMapper.selectList(new LambdaQueryWrapper&lt;Address&gt;()
     *           .eq(Address::getUserId, userId)
     *           .orderByDesc(Address::getIsDefault)
     *           .orderByDesc(Address::getId));
     *   return list.stream().map(AddressVO::of).toList();
     *
     * 为什么不先查默认地址再查其余：一次查询 + 两条排序规则就够了，
     * 分两次查还要在 Java 里合并，纯属给自己找 bug。
     * 为什么不分页：最多 20 条，前端 picker 直接铺开。分页会让下拉框长出一个「加载更多」。
     * 一条都没有时返回空 List，不是 404 —— 空列表是正常结果，不是错误。
     * </pre>
     */
    public List<AddressVO> list(Long userId) {
        // 按 is_default、id 倒序：默认地址在最前，其余新的在前
        List<Address> list =addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId,userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getId));
        return list.stream().map(AddressVO::of).toList();
    }

    /**
     * 新增一条地址。
     *
     * <pre>
     * 步骤 2：
     * 1) 先数数：
     *    addressMapper.selectCount(new LambdaQueryWrapper&lt;Address&gt;().eq(Address::getUserId, userId))
     *    &gt;= MAX_ADDRESS → new BusinessException("最多保存 20 个收货地址")。
     * 2) 要设默认（Integer.valueOf(1).equals(dto.getIsDefault())）→ 先 clearDefault(userId)。
     *    已经被逻辑删除的地址不用管：MyBatis-Plus 的 update 会自动补 is_deleted = 0，
     *    被删的行留着 is_default = 1 也不影响查询（它本来就查不出来）。
     * 3) 如果这是该用户的第一条地址（第 1 步的 count == 0），强制 is_default = 1。
     *    否则用户第一次下单，默认地址是空的，前端得让他在空列表里选。
     * 4) 拼 Address 后 insert：userId 一定用参数里的，绝对不要从 dto 拿（dto 里根本没有）；
     *    insert 之后 address.getId() 会被回填（@TableId(type = IdType.AUTO)），
     *    直接 return AddressVO.of(address)。
     * </pre>
     *
     * <p>为什么要 @Transactional：清默认 + 插新地址是两次写。
     * 中间挂了会留下「一条默认都没有」的状态。注意 @Transactional 只对 public 方法生效，
     * 且同类内部自调用（this.xxx()）不走代理 = 注解白写，所以事务边界留在这些 public 方法上。
     */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO create(Long userId, AddressSaveDTO dto) {
        // 数已有几条 → 满了拒 → 要设默认先清旧 → 插库回填 id
        Long count = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId));
        if (count >=MAX_ADDRESS) {
            throw new BusinessException("最多保存 20 个收货地址");
        }
        boolean wantDefault = Integer.valueOf(1).equals(dto.getIsDefault());
        boolean first= count == 0;
        if (wantDefault && !first) {
            clearDefault(userId);
        }
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName(dto.getReceiverName());
        address.setPhone(dto.getPhone());
        address.setRegion(dto.getRegion());
        address.setDetail(dto.getDetail());
        address.setIsDefault((first || wantDefault) ? 1 : 0);
        addressMapper.insert(address);
        return AddressVO.of(address);
    }

    /**
     * 修改一条地址（整体替换，不是局部更新）。
     *
     * <pre>
     * 步骤 3：
     * 1) Address addr = addressMapper.selectById(id)；
     *    addr == null || !Objects.equals(addr.getUserId(), userId) → 404 "地址不存在"。
     * 2) 把 dto 的 receiverName / phone / region / detail 四个字段抄进 addr。
     * 3) isDefault：只有传 1 才动作 —— 先 clearDefault(userId)，再 addr.setIsDefault(1)；
     *    传 0 / 不传就保持 addr 原来的值（「取消默认」不支持，见 AddressSaveDTO 注释）。
     * 4) addressMapper.updateById(addr)：实体有 @TableLogic，MP 会自动补 is_deleted = 0 条件。
     * 5) return AddressVO.of(addr) —— update 不回填字段，但 addr 就是你要的值。
     * </pre>
     *
     * <p>顺序别改：先 clearDefault 再 updateById。反过来的话，
     * 如果你这条本来就是默认地址，最后一条 SQL 会把它又清成 0 ——
     * 「结果由最后一条语句决定」，这是写 UPDATE 顺序时最容易翻车的地方。
     */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO update(Long userId, Long id, AddressSaveDTO dto) {
        // 查 → 判归属 → 覆盖四字段 → 需要设默认时先清后设 → 更新
        Address addr = addressMapper.selectById(id);
        if (addr == null||!Objects.equals(addr.getUserId(), userId)) {
            throw new BusinessException(404,"地址不存在");
        }
        addr.setReceiverName(dto.getReceiverName());
        addr.setPhone(dto.getPhone());
        addr.setRegion(dto.getRegion());
        addr.setDetail(dto.getDetail());
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(userId);
            addr.setIsDefault(1);
        }
        addressMapper.updateById(addr);


        return AddressVO.of(addr);

    }

    /**
     * 删除一条地址（逻辑删除）。
     *
     * <pre>
     * 步骤 4：
     * 1) selectById + 归属校验，同 update（查不到 / 不是自己的统一 404）。
     * 2) addressMapper.deleteById(id) —— 实体上有 @TableLogic，这其实是 UPDATE is_deleted = 1，
     *    行还在库里，只是以后查不出来了（历史订单里那四个收货字段是快照，不受影响）。
     * 3) 如果删掉的正好是默认地址（addr.getIsDefault() == 1），要把剩下最新的一条提上来：
     *      addressMapper.selectOne(new LambdaQueryWrapper&lt;Address&gt;()
     *              .eq(Address::getUserId, userId)
     *              .orderByDesc(Address::getId)
     *              .last("LIMIT 1"));
     *    查到就 setIsDefault(1) + updateById；没有剩余就什么都不做（用户没地址了，正常）。
     *    注意 selectOne 在查出多条时会抛异常，所以要 last("LIMIT 1") 兜住。
     * </pre>
     *
     * <p>删第二次同一个 id 会得到 404：selectById 已经查不到（逻辑删除的行被过滤）。
     * 这是预期行为，不是 bug —— 前端列表里本来也没它了。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        // 查 → 判归属 → 删除；删的是默认地址就提一条上来
        Address address = addressMapper.selectById(id);
        if (address == null||!Objects.equals(address.getUserId(), userId)) {
            throw new BusinessException(404,"地址不存在");
        }
        boolean wasDefault = Integer.valueOf(1).equals(address.getIsDefault());
        addressMapper.deleteById(id);
        if (wasDefault) {
            Address next = addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                    .eq(Address::getUserId, userId)
                    .orderByDesc(Address::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                next.setIsDefault(1);
                addressMapper.updateById(next);
            }
        }
    }

    /**
     * 把某条地址设为默认（PATCH /address/{id}/default）。
     *
     * <pre>
     * 步骤 5：
     * 1) selectById + 归属校验 → 404。
     * 2) 幂等：本来就 is_default == 1 → 直接 return AddressVO.of(addr)，别白写两次库。
     * 3) clearDefault(userId) → addr.setIsDefault(1) → updateById(addr) → return VO。
     * </pre>
     *
     * <p>为什么单独开一个接口而不是复用 PUT：前端列表里点「设为默认」不该逼用户
     * 把整条地址重新提交一遍（也拿不到最新值）。小接口 + 明确语义，比一个万能 PUT 好维护。
     */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO setDefault(Long userId, Long id) {
        // 查 → 判归属 → 已是默认直接返回 → 先清旧默认再置 1
        Address address = addressMapper.selectById(id);
        if (address == null||!Objects.equals(address.getUserId(), userId)) {
            throw new BusinessException(404,"地址不存在");
        }
        if (Integer.valueOf(1).equals(address.getIsDefault())) {
            return AddressVO.of(address);
        }
        clearDefault(userId);
        address.setIsDefault(1);
        addressMapper.updateById(address);

        return AddressVO.of(address);



    }

    /**
     * 给 OrderService 用的：拿到一条属于该用户的地址，拿不到就 404。
     *
     * <pre>
     * 步骤 6：
     *   Address addr = addressMapper.selectById(addressId);
     *   if (addr == null || !Objects.equals(addr.getUserId(), userId)) {
     *       throw new BusinessException(404, "收货地址不存在");
     *   }
     *   return addr;
     * </pre>
     *
     * <p>为什么返回实体而不是 VO：下单要把四个收货字段拷成快照，
     * VO 少了 userId（没法判断归属）也少了「这就是库里那一行」的确定感。
     * P1 这个规模，跨 Service 传实体是划算的；等拆成多个模块再谈 DTO 隔离。
     */
    public Address requireOwnAddress(Long userId, Long addressId) {
        // 查不到 / 不是本人的地址，统一 404
        Address addr = addressMapper.selectById(addressId);
        if (addr == null || !Objects.equals(addr.getUserId(), userId)) {
            throw new BusinessException(404, "收货地址不存在");
        }
        return addr;
    }

    /**
     * 把这个用户所有地址的 is_default 清成 0（create / update / setDefault 共用）。
     *
     * <pre>
     * 步骤 7：
     *   addressMapper.update(null, new LambdaUpdateWrapper&lt;Address&gt;()
     *           .eq(Address::getUserId, userId)
     *           .eq(Address::getIsDefault, 1)
     *           .set(Address::getIsDefault, 0));
     * 需要 import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper。
     * 第一个参数传 null 表示「不按实体更新」，只用 wrapper 里的 set。
     * 别在这里加 is_deleted = 0 的条件，MP 已经替你加了（逻辑删除字段在 update 时也会补）。
     * </pre>
     */
    private void clearDefault(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1)
                .set(Address::getIsDefault, 0));
    }
}
