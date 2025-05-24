package com.hmdp.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;

/**
 * @program: hmdp
 * @description:
 * @author: ljl
 * @create: 2025-05-24 11:08
 **/

public class JwtUtils {
    private static final long EXPIRE_TIME = 1000 * 60 * 60; // 1小时
    private static final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    // 生成 Token
    public static String generateToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }

    // 校验 Token
    public static Jws<Claims> parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token);
    }

    // 获取用户 ID
    public static String getUserId(String token) {
        return parseToken(token).getBody().getSubject();
    }

    // 获取 Token 过期时间
    public static Date getExpiration(String token) {
        return parseToken(token).getBody().getExpiration();
    }
}
