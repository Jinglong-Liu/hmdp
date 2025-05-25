package com.hmdp.service;

import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {
    Shop queryById(Long id);

    Shop queryWithMutex(Long id);

    boolean update(Shop shop);

    // 逻辑过期
    void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException;
}
