package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtUtils;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    // 如果变量名是redisTemplate
    // @Resource(name = "stringRedisTemplate")
    // 改成Autowired也可
    // 直接用Resource, name就会是redisTemplate，找到的是RedisTemplate的bean，导致类型不匹配
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = String.valueOf(new Random().nextInt(899999) + 100000);
        // redis记录验证码有效
        stringRedisTemplate.opsForValue().set("login:code:" + phone, code, Duration.ofMinutes(5));

        // 模拟发送短信
        log.debug("验证码发送到 " + phone + "，验证码是：" + code);
        return Result.ok("验证码发送成功:" + code);
    }

    @Override
    public Result login(String phone, String code) {
        String cacheCode = stringRedisTemplate.opsForValue().get("login:code:" + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.ok(Map.of("error", "验证码错误或过期")).status(401);
        }

        // 登录成功，生成 JWT
        String token = JwtUtils.generateToken(phone);

        return Result.ok(Map.of("token", token));
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.fail("没有token");
        }

        String token = authHeader.substring("Bearer ".length());
        // 获取 token 过期时间
        Date expiration = JwtUtils.getExpiration(token);
        long ttl = expiration.getTime() - System.currentTimeMillis();

        // 将 token 加入 Redis 黑名单，设置过期时间为 token 剩余时间
        stringRedisTemplate.opsForValue().set("jwt:blacklist:" + token, "1", ttl, TimeUnit.MILLISECONDS);

        return Result.ok("退出成功");
    }
}
