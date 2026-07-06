package com.lumira.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping({"/api/v1/auth/wechat", "/api/v2/auth/wechat"})
public class AuthWechatCallbackController {

    @GetMapping("/callback")
    public void callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            HttpServletResponse response
    ) throws IOException {
        String target = "/user/login";
        if (StringUtils.hasText(code) && StringUtils.hasText(state)) {
            target += "?code=" + encode(code.trim()) + "&state=" + encode(state.trim());
        }
        response.sendRedirect(target);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
