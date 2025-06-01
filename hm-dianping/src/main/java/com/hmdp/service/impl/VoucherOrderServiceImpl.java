package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.AopProxy;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ljl
 * @since 2025-06-01
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    @Lazy
    private IVoucherOrderService voucherOrderService;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. 判断秒杀时间范围
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
        // 3. 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        // 获取redis锁对象
        // 对userId加锁，确保每个人的这个线程执行完前，会被阻塞
        final RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
        // 直接tryLock()，看门狗自动续期
        // boolean success = lock.tryLock();
        // boolean success = lock.tryLock(1, TimeUnit.SECONDS);
        boolean success = lock.tryLock(1, 10, TimeUnit.SECONDS);
        // 如果未获取到锁，直接返回用户不可重复下单
        if (!success) {
            return Result.fail("用户不可重复下单购买，一人只能买一次");
        }
        try {
            // 一样
            // return voucherOrderService.createVoucherOrder(voucher.getVoucherId());
            final IVoucherOrderService iVoucherOrderService = (IVoucherOrderService)AopContext.currentProxy();
            return iVoucherOrderService.createVoucherOrder(voucher.getVoucherId());
        } finally {
            lock.unlock();
        }
    }

    // 注意
    // 不加也不行
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    // @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("该用户已抢购过该优惠券");
        }
        // 更新时判断是否库存是否大于0 乐观锁
        boolean success = seckillVoucherService.update().setSql(" stock = stock - 1").gt("stock", 0)
                .eq("voucher_id", voucherId).update();
        if (!success) {
            return Result.fail("优惠券秒杀完毕库存不足！！！");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUpdateTime(LocalDateTime.now());
        save(voucherOrder);
        return Result.ok("抢购成功");
    }
}
