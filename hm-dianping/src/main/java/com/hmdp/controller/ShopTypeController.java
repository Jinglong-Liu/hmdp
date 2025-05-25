package com.hmdp.controller;


import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    private RedisTemplate redisTemplate;
    @GetMapping("list")
    public Result queryTypeList() {
        // 1. 从 Redis 中获取 zset 的所有元素（score 从 0 到 +∞）
        Set<ZSetOperations.TypedTuple<String>> cachedSet =
                redisTemplate.opsForZSet().rangeWithScores(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        // 2. 判断是否命中
        if (cachedSet != null && !cachedSet.isEmpty()) {
            // 反序列化
            List<ShopType> typeList = cachedSet.stream()
                    .map(tuple -> JSONUtil.toBean(tuple.getValue(), ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(typeList);
        }
        // 3. 未命中，查询数据库
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        // 4. 写入 Redis zset
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        for (ShopType type : typeList) {
            zSetOps.add(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(type), type.getSort());
        }

        // 可选：设置过期时间
        redisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY, 30, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
