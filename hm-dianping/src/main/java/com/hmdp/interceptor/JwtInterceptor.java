package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.JwtUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * @program: hmdp
 * @description:
 * @author: ljl
 * @create: 2025-05-24 11:07
 **/

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        Result result = Result.fail(message).status(401);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "未登录");
            return false;
        }

        String token = authHeader.substring(7);

        // 判断是否在黑名单中
        // 可以删了
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey("jwt:blacklist:" + token))) {
            sendUnauthorized(response, "请重新登录");
            return false;
        }

        try {
            JwtUtils.parseToken(token);
        } catch (Exception e) {
            sendUnauthorized(response, "Token非法");
            return false;
        }
        // 从redis中取user
        String redisKey = SystemConstants.LOGIN_USER + token;
        final Map<Object, Object> userMap = redisTemplate.opsForHash().entries(redisKey);
        if (userMap.isEmpty()) {
            sendUnauthorized(response, "未登录或登录已过期");
        }
        // 转换成userDTO
        final UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 保存到 ThreadLocal
        UserHolder.saveUser(userDTO);
        // 刷新token有效期
        stringRedisTemplate.expire(redisKey, Duration.ofMinutes(30));
        // 放行
        return true;
    }
}
