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
     * <p><b>接口</b>：<code>GET /dev-api/address</code>，带 token；没有请求体，也没有参数。
     * <pre>
     * 成功    {"code":200,"msg":"ok","data":[
     *           {"id":3,"receiverName":"张三","phone":"138…","region":"北京 海淀",
     *            "detail":"3 号楼 501","isDefault":1},
     *           {"id":1,"receiverName":"李四","isDefault":0}]}
     * </pre>
     *
     * <p><b>会改的数据</b>：没有，纯读。
     *
     * <p><b>失败情况</b>：没有业务失败；未登录由 JwtInterceptor 拦在外面。
     *
     * <p><b>注意</b>：不分页 —— 每人最多 20 条，前端 picker 直接铺开，分页会让下拉框长出「加载更多」。
     * 一条都没有时返回空数组，不是 404：空列表是正常结果，不是错误。
     */
    public List<AddressVO> list(Long userId) {
        // 1. 一次查询 + 两条排序规则：默认地址排最前，其余按 id 倒序（新的在前）
        //    ps：别先查默认地址再查其余，两次查询还要在 Java 里合并，纯属给自己找 bug
        List<Address> list = addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId,userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getId));
        return list.stream().map(AddressVO::of).toList();
    }

    /**
     * 新增一条地址（这个人还没有地址时，第一条自动成为默认）。
     *
     * <p><b>接口</b>：<code>POST /dev-api/address</code>，带 token。
     * <pre>
     * 请求体  {"receiverName":"张三","phone":"13800000000","region":"北京 海淀",
     *          "detail":"3 号楼 501","isDefault":1}
     *          四个联系字段必填、有限长；isDefault 可选：1 = 设为默认，其他/不传 = 普通地址
     * 成功    {"code":200,"msg":"ok","data":{"id":3,"receiverName":"张三","isDefault":1}}
     * </pre>
     *
     * <p><b>会改的数据</b>
     * <ul>
     *   <li>address：新增 1 行，user_id = 当前登录人</li>
     *   <li>address：如果设了默认，同一个人其它地址 is_default 1 → 0</li>
     * </ul>
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>400 最多保存 20 个收货地址</li>
     * </ul>
     *
     * <p><b>注意</b>：@Transactional 必须有 —— 「清旧默认 + 插新地址」是两次写，
     * 中间挂了会留下「一条默认都没有」的状态。而且注解只对 public 方法生效、
     * 同类内自调用不走代理，所以事务边界就放在这些 public 方法上。
     */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO create(Long userId, AddressSaveDTO dto) {
        // 1. 先数已有几条，满了直接拒
        //    ps：上限 20 不只是产品限制，也顺手挡住有人拿这个接口往表里灌数据
        Long count = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId));
        if (count >=MAX_ADDRESS) {
            throw new BusinessException("最多保存 20 个收货地址");
        }
        // 2. 第一条地址强制设成默认
        //    ps：否则用户第一次下单，默认地址是空的，前端得让他在空列表里选
        boolean wantDefault = Integer.valueOf(1).equals(dto.getIsDefault());
        boolean first= count == 0;
        // 3. 要设默认就先清旧的，再插新的
        //    ps：已经被逻辑删除的地址不用管 —— MP 的 update 会自动补 is_deleted = 0，
        //        被删的行留着 is_default = 1 也查询不出来
        if (wantDefault && !first) {
            clearDefault(userId);
        }
        Address address = new Address();
        //    ps：userId 一定用参数里的，dto 里根本没有这个字段（防止前端指定「给谁存地址」）
        address.setUserId(userId);
        address.setReceiverName(dto.getReceiverName());
        address.setPhone(dto.getPhone());
        address.setRegion(dto.getRegion());
        address.setDetail(dto.getDetail());
        address.setIsDefault((first || wantDefault) ? 1 : 0);
        //    ps：insert 之后 id 会被回填（@TableId(type = IdType.AUTO)），所以直接能给 VO
        addressMapper.insert(address);
        return AddressVO.of(address);
    }

    /**
     * 修改一条地址（整体替换，不是局部更新）。
     *
     * <p><b>接口</b>：<code>PUT /dev-api/address/{id}</code>，带 token；请求体同「新增」。
     * <pre>
     * 请求体  {"receiverName":"张三","phone":"13800000001","region":"北京 朝阳",
     *          "detail":"5 号楼 302","isDefault":1}
     * 成功    {"code":200,"msg":"ok","data":{"id":3,"receiverName":"张三","isDefault":1}}
     * </pre>
     *
     * <p><b>会改的数据</b>：address 一条 —— 四个联系字段整体覆盖；传 isDefault=1 时
     * 同一个人其它地址 is_default 1 → 0。「取消默认」不支持，见 AddressSaveDTO 注释。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 地址不存在 —— 查不到，或者不是自己的（用 404 不用 403：403 会泄露「这个 id 存在」）</li>
     * </ul>
     *
     * <p><b>注意</b>：先 clearDefault 再 updateById，顺序别换 —— 反过来的话，
     * 如果这条本来就是默认地址，最后一条 UPDATE 会把它又清成 0。
     * 「结果由最后一条语句决定」，这是写多条 UPDATE 时最容易翻车的地方。
     */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO update(Long userId, Long id, AddressSaveDTO dto) {
        // 1. 查 + 判归属：查不到 / 不是自己的，统一 404
        Address addr = addressMapper.selectById(id);
        if (addr == null||!Objects.equals(addr.getUserId(), userId)) {
            throw new BusinessException(404,"地址不存在");
        }
        // 2. 四个字段整体覆盖 —— 前端编辑页会把整条地址提交上来，不是局部更新
        addr.setReceiverName(dto.getReceiverName());
        addr.setPhone(dto.getPhone());
        addr.setRegion(dto.getRegion());
        addr.setDetail(dto.getDetail());
        // 3. 只有传 1 才动默认：先清后设
        //    ps：updateById 时 MP 会按 @TableLogic 自动补 is_deleted = 0
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(userId);
            addr.setIsDefault(1);
        }
        addressMapper.updateById(addr);


        return AddressVO.of(addr);

    }

    /**
     * 删除一条地址（逻辑删除）。删掉的如果是默认地址，会把剩下最新的一条提上来。
     *
     * <p><b>接口</b>：<code>DELETE /dev-api/address/{id}</code>，带 token；没有请求体。
     * <p><b>成功</b>：{"code":200,"msg":"ok","data":null}
     *
     * <p><b>会改的数据</b>：address.is_deleted 0 → 1（行还在库里）；
     * 删的正好是默认地址时，剩下最新的一条 is_default 0 → 1。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 地址不存在 —— 查不到 / 不是自己的 / 已经删过一次了</li>
     * </ul>
     *
     * <p><b>注意</b>：历史订单里那四个收货字段是快照，删地址不影响它们。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        // 1. 查 + 判归属
        //    ps：删第二次同一个 id 会 404（逻辑删除的行被过滤掉）—— 预期行为，不是 bug
        Address address = addressMapper.selectById(id);
        if (address == null||!Objects.equals(address.getUserId(), userId)) {
            throw new BusinessException(404,"地址不存在");
        }
        // 2. 真删之前先记住它是不是默认地址
        //    ps：deleteById 对 @TableLogic 实体其实是 UPDATE is_deleted = 1，不是物理删
        boolean wasDefault = Integer.valueOf(1).equals(address.getIsDefault());
        addressMapper.deleteById(id);
        if (wasDefault) {
            // 3. 删的是默认地址 → 把剩下最新的一条提上来，别让用户「没有默认地址」
            //    ps：selectOne 查出多条会抛异常，所以必须 last("LIMIT 1") 兜住
            Address next = addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                    .eq(Address::getUserId, userId)
                    .orderByDesc(Address::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                next.setIsDefault(1);
                addressMapper.updateById(next);
            }
            //    ps：没有剩余地址就什么都不做 —— 用户没地址了，正常
        }
    }

    /**
     * 把某条地址设为默认。
     *
     * <p><b>接口</b>：<code>PATCH /dev-api/address/{id}/default</code>，带 token；没有请求体。
     * <p><b>成功</b>：{"code":200,"msg":"ok","data":{"id":3,…,"isDefault":1}}
     *
     * <p><b>会改的数据</b>：address 最多两条 —— 这条 is_default 0 → 1，原来的默认 1 → 0。
     *
     * <p><b>失败情况</b>
     * <ul>
     *   <li>404 地址不存在</li>
     * </ul>
     *
     * <p><b>注意</b>：幂等 —— 本来就是默认就直接返回 VO，不白写两次库。
     * 为什么单独开一个接口：前端列表里点「设为默认」不该逼用户把整条地址重新提交一遍
     * （编辑页里的值可能已经过期）。小接口 + 明确语义，比一个万能 PUT 好维护。
     */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO setDefault(Long userId, Long id) {
        // 1. 查 + 判归属
        Address address = addressMapper.selectById(id);
        if (address == null||!Objects.equals(address.getUserId(), userId)) {
            throw new BusinessException(404,"地址不存在");
        }
        // 2. 已经是默认就直接返回，别再写两次库
        if (Integer.valueOf(1).equals(address.getIsDefault())) {
            return AddressVO.of(address);
        }
        // 3. 先清旧默认，再置 1
        clearDefault(userId);
        address.setIsDefault(1);
        addressMapper.updateById(address);

        return AddressVO.of(address);



    }

    /**
     * 给 OrderService 用的：拿到一条属于该用户的地址，拿不到就 404。
     *
     * <p><b>被谁调用</b>：{@link OrderService#create} —— 下单时把四个收货字段拷成订单快照。
     * <p><b>前置条件</b>：addressId 必须是这个 userId 名下的，且没被逻辑删除。
     * <p><b>幂等</b>：纯读，天然幂等。
     *
     * <p><b>为什么返回实体而不是 VO</b>：下单要拷四个字段，VO 少了 userId（没法判归属），
     * 也不像「这就是库里那一行」那么确定。P1 这个规模，跨 Service 传实体是划算的；
     * 等以后拆成多个模块再谈 DTO 隔离。
     */
    public Address requireOwnAddress(Long userId, Long addressId) {
        // ps：查不到 / 不是本人的地址统一 404 —— 别回 403，403 等于告诉对方「这个 id 存在」
        Address addr = addressMapper.selectById(addressId);
        if (addr == null || !Objects.equals(addr.getUserId(), userId)) {
            throw new BusinessException(404, "收货地址不存在");
        }
        return addr;
    }

    /**
     * 把这个用户所有地址的 is_default 清成 0（create / update / setDefault 共用）。
     *
     * <p><b>被谁调用</b>：{@link #create}、{@link #update}、{@link #setDefault}。
     * <p><b>为什么单独抽出来</b>：address 表没有「默认地址只能一条」的唯一索引
     * （逻辑删除下会误伤），只能靠「先全清 0、再置 1」在同一个事务里完成，三处都要用到。
     * <p><b>注意</b>：第一个参数传 null 表示「不按实体更新」，只用 wrapper 里的 set；
     * 别自己加 is_deleted = 0 的条件，MP 在 update 时也会补逻辑删除条件。
     */
    private void clearDefault(Long userId) {
        // ps：update 的第一参传 null = 不按实体更新，只用 wrapper 里的 set
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1)
                .set(Address::getIsDefault, 0));
    }
}
