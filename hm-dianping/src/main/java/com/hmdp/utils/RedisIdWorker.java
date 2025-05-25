package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @program: hmdp
 * @description:
 * @author: ljl
 * @create: 2025-05-25 23:29
 **/

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String prefix) {
        // 1  bit 符号位 0
        // 31 bit 时间戳(秒)
        // 32 bit 序列号
        // 拼接

        // 1.时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.序列号 自增
        // redis 支持64 bit，但此处要求32 bit内
        // 2.1 日期
        // 方便之后统计
        final String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 不会空指针，不存在会自动创建，从0开始+1
        long count = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + date);

        // 3.拼接，返回long

        return (timestamp << COUNT_BITS) | count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022,1,1,0,0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        // 1640995200
        System.out.println(second);
    }
}
