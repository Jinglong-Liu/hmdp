package com.hmdp.interceptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.utils.JwtUtils;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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

    private void unAuthorized(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        Result result = Result.fail("未登录").status(401);
        ObjectMapper mapper = new ObjectMapper();
        String json = null;
        try {
            json = mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        // 写入响应体
        try {
            response.getWriter().write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            unAuthorized(response);
            // response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        String token = authHeader.substring(7);

        // 判断是否在黑名单中
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey("jwt:blacklist:" + token))) {
            unAuthorized(response);
            // response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        try {
            JwtUtils.parseToken(token);
            return true;
        } catch (Exception e) {
            unAuthorized(response);
            //response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
    }
}
