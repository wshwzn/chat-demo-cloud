package com.example.chatdemo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/** 固定口令鉴权：请求头 X-Auth-Token 必须等于 app.token；token 为空时不校验。 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private final String token;

    public TokenInterceptor(@Value("${app.token:}") String token) {
        this.token = token;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (token == null || token.isBlank()) {
            return true;
        }
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String provided = request.getHeader("X-Auth-Token");
        if (token.equals(provided)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write("{\"error\":\"未授权\"}".getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
