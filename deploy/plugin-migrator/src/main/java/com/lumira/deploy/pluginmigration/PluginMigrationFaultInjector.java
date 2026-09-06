package com.lumira.deploy.pluginmigration;

@FunctionalInterface
interface PluginMigrationFaultInjector {

    void afterDdlBeforeVerify() throws InterruptedException;

    static PluginMigrationFaultInjector noOp() {
        return () -> { };
    }

    static PluginMigrationFaultInjector delayedAfterDdl(int delayMillis) {
        if (delayMillis < 1 || delayMillis > 120_000) {
            throw new IllegalArgumentException("fault injection delay must be between 1 and 120000 milliseconds");
        }
        return () -> Thread.sleep(delayMillis);
    }
}
