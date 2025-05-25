package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private CacheClient cacheClient;
    @Test
    public void testSaveShop() throws InterruptedException {
        // shopService.saveShop2Redis(1L, 10L);
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpired(RedisConstants.CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.MINUTES);
    }
}
