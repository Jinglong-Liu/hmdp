package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @program: hmdp
 * @description:
 * @author: ljl
 * @create: 2025-05-25 21:56
 **/

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public void setWithLogicalExpired(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> callback, Long time, TimeUnit unit) {
        // redis查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        // 存在，直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // redis存储空值，防止穿透
        if ("".equals(json)) {
            return null;
        }
        // 4.不存在，查询数据库
        final R r = callback.apply(id);
        if (r == null) {
            // 存空值
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(key, r, time, unit);
        return r;
    }
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 拆箱防止空指针
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
    public <R, ID> R queryWithLogicalExpired(String keyPrefix, ID id, Class<R> type, Function<ID, R> callback, Long time, TimeUnit unit) {
        // redis查询缓存
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        // 需要预热
        // TODO: 处理一下不存在的情况, 不然必须预热
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 4.命中
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回
            return r;
        }
        // 5.2 已过期，缓存重建
        // 6.缓存重建
        // 互斥锁

        String lockKey = keyPrefix + id;
        final boolean success = tryLock(lockKey);
        if (success) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 测试,实际30分钟
                try {
                    // db 读
                    R r1 = callback.apply(id);
                    // 写redis
                    this.setWithLogicalExpired(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return r;
    }
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

}
