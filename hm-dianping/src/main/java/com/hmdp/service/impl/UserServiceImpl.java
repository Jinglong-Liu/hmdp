package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.*;
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

    @Resource
    private RedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = String.valueOf(new Random().nextInt(899999) + 100000);
        // redis记录验证码有效
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, Duration.ofMinutes(5));

        // 模拟发送短信
        log.debug("验证码发送到 " + phone + "，验证码是：" + code);
        return Result.ok("验证码发送成功:" + code);
    }

    @Override
    public Result login(String phone, String code) {
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.ok(Map.of("error", "验证码错误或过期")).status(401);
        }

        // 登录成功，生成 JWT
        String token = JwtUtils.generateToken(phone);

        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((name, value) -> value.toString()));
        // 存redis
        String key = SystemConstants.LOGIN_USER + token;
        redisTemplate.opsForHash().putAll(key, userMap);
        redisTemplate.expire(key, Duration.ofMinutes(30));
        return Result.ok(Map.of("token", token));
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // mybatis-plus
        save(user);
        return user;
    }
    @Override
    public Result logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.fail("没有token");
        }

        String token = authHeader.substring("Bearer ".length());
        if (StringUtils.isBlank(token)) {
            return Result.fail("无效请求");
        }
        // 删除登录信息
        redisTemplate.delete(SystemConstants.LOGIN_USER + token);
        // 获取 token 过期时间
        Date expiration = JwtUtils.getExpiration(token);
        long ttl = expiration.getTime() - System.currentTimeMillis();

        // 将 token 加入 Redis 黑名单，设置过期时间为 token 剩余时间
        stringRedisTemplate.opsForValue().set("jwt:blacklist:" + token, "1", ttl, TimeUnit.MILLISECONDS);

        return Result.ok("退出成功");
    }

    public void saveUserToRedis(UserDTO user, String token) {
        String key = "login:user:" + token;

        Map<String, String> map = new HashMap<>();
        map.put("id", user.getId().toString());
        map.put("nickName", user.getNickName());
        map.put("icon", user.getIcon());

        redisTemplate.opsForHash().putAll(key, map);

        // 设置过期时间（30分钟）
        redisTemplate.expire(key, Duration.ofMinutes(30));
    }
}
