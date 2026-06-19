package com.lumira.saas.modules.plugin.event;

public interface PluginOutboxDispatcher {
    void dispatch(PluginOutboxRow row);
}
