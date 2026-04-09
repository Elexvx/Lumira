package com.example.plugins.sms;

import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginBootstrap;

public class SmsPluginBootstrap implements PluginBootstrap {

    @Override
    public void initialize(PluginRuntimeContext context) {
        // 迁移脚本负责建表，这里保持轻量初始化即可。
    }
}
