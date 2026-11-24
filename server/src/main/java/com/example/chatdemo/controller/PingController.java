package com.example.chatdemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PingController {

    /** 供前端「测试连接」使用，不需要鉴权 */
    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "service", "chat-demo-server", "time", System.currentTimeMillis());
    }
}
