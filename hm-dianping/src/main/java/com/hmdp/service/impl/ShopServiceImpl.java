package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Shop queryById(Long id) {
        // 解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, (id2) -> {
//            return getById(id2);
//        }, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpired(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, (id2) -> {
            return getById(id2);
        }, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    @Override
    public Shop queryWithMutex(Long id) {
        // redis查询缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        // 存在，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 不存在，查询数据库
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            final boolean isLock = tryLock(lockKey);
            if (!isLock) {
                Thread.sleep(50);
                // TODO: 重试
            }
            Shop shop = getById(id);
            // 数据库不存在
            if (shop == null) {
                // 写空值
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            } else {
                // 存在，写入redis，返回
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
            return shop;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
    }

    public Shop queryWithLogicalExpire(Long id) {
        // redis查询缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        // 存在，直接返回
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 4.命中
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回
            return shop;
        }
        // 5.2 已过期，缓存重建
        // 6.缓存重建
        // 互斥锁

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        final boolean success = tryLock(lockKey);
        if (success) {
            CacheClient.CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 测试,实际30分钟
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // TODO: else 重试
        return shop;
    }

    @Override
    public boolean update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return false;
        }
        // 1、更新数据库
        updateById(shop);
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        // 2、删除缓存
        stringRedisTemplate.delete(key);
        // 3、返回
        return true;
    }
    // 逻辑过期
    @Override
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Thread.sleep(200);
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 拆箱防止空指针
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
