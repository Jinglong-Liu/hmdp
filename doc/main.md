# 项目进展

# 初始版本开发
## v0.0.1
导入初始版本，导入SQL，修改配置，运行起来 (使用jdk11)

测试接口
```
/shop-type/list
```

```json
{
    "success": true,
    "data": [
        {
            "id": 1,
            "name": "美食",
            "icon": "/types/ms.png",
            "sort": 1
        },
        {
            "id": 2,
            "name": "KTV",
            "icon": "/types/KTV.png",
            "sort": 2
        },
        {
            "id": 3,
            "name": "丽人·美发",
            "icon": "/types/lrmf.png",
            "sort": 3
        },
        {
            "id": 10,
            "name": "美睫·美甲",
            "icon": "/types/mjmj.png",
            "sort": 4
        },
        {
            "id": 5,
            "name": "按摩·足疗",
            "icon": "/types/amzl.png",
            "sort": 5
        },
        {
            "id": 6,
            "name": "美容SPA",
            "icon": "/types/spa.png",
            "sort": 6
        },
        {
            "id": 7,
            "name": "亲子游乐",
            "icon": "/types/qzyl.png",
            "sort": 7
        },
        {
            "id": 8,
            "name": "酒吧",
            "icon": "/types/jiuba.png",
            "sort": 8
        },
        {
            "id": 9,
            "name": "轰趴馆",
            "icon": "/types/hpg.png",
            "sort": 9
        },
        {
            "id": 4,
            "name": "健身运动",
            "icon": "/types/jsyd.png",
            "sort": 10
        }
    ]
}
```

commit: 04cfa023197b0be633d67c9eac5579a7ab08c30f

可以进入开发阶段

## 0.1 登录登出，权限校验

模拟验证码发送。

登录校验采用jwt的方式，登出采用黑名单(user+token)。

导入openapi.json到apifox进行测试

注意设置前置后置（登录，token记录到变量，然后带上）

测试：需要mysql, redis
```
1、未登录状态测试查询商铺接口，返回401
2、测试发送验证码，返回验证码
3、测试手机号+验证码登录，成功
4、重新测试查询商铺接口，返回成功
5、登出成功
6、重新测试查询商铺接口或登出接口，返回401
```

## 0.1.1 登录保存用户信息

- 登录如果手机号不存在，自动注册并登录

- 用户信息保存在redis，每次请求，拦截器读取并放到ThreadLocal

- key由前缀和jwt的token决定

- 使用redis hash类型

- 登出只需要删除接口

- 新增校验接口 /user/me，返回用户信息

# 0.2 缓存

初步使用缓存

缓存了店铺类型和店铺信息

店铺类型：使用zset（因为需要排序）
GET /shop-type/list

店铺信息：string类型
GET /shop/{id}

更新店铺接口
PUT /shop

先更新数据库，再删除缓存

# 0.2.1 缓存优化

使用缓存空对象解决缓存穿透

TODO: 使用布隆过滤器实现

使用逻辑过期时间解决缓存击穿（热点key)，目前需要预热（见test）

封装CacheClient

TODO: 锁失败重试；缓存隔离，限流，降级

apifox 为获取验证码接口添加后置操作，提取变量给登录接口用

# 0.3.1 全局唯一id

利用redis 自增

UUID, redis自增 snowflake算法，UUID，leaf

redis自增：0 + 时间戳 + 自增id，每天一个key

# 0.3.2 下单 乐观锁

```java
boolean success = seckillVoucherService.update()
        .setSql("stock = stock - 1")
        .eq("voucher_id", voucherId)
        // 乐观锁
        .eq("stock", voucher.getStock())
        .update();
```
这样失败率很高
```java
boolean success = seckillVoucherService.update()
        .setSql("stock = stock - 1")
        .eq("voucher_id", voucherId)
        // 乐观锁, 只需要判断 > 0 就行
        .gt("stock", 0)
        .update();
```
这样好

# 0.4.1 秒杀

注意事务传播机制
```java
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

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        // ...
        final RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
        boolean success = lock.tryLock(1, 10, TimeUnit.SECONDS);
        try {
            IVoucherOrderService iVoucherOrderService = (IVoucherOrderService)AopContext.currentProxy();
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
        // 抢购
    }
}

```

压测单人抢购和多人抢购均通过

