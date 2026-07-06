package com.lumira.saas.modules.plugin.controller;

import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.event.PluginOutboxRelay;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalJobControllerTest {

    @Test
    void replayOutboxShouldRejectInvalidIdBeforeRelayCall() {
        PluginOutboxRelay relay = mock(PluginOutboxRelay.class);
        InternalJobController controller = new InternalJobController(relay, "plugin-secret");

        assertThatThrownBy(() -> controller.replayOutbox(0L, "plugin-secret"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(relay);
    }

    @Test
    void relayOutboxShouldRejectOversizedTokenBeforeRelayCall() {
        PluginOutboxRelay relay = mock(PluginOutboxRelay.class);
        InternalJobController controller = new InternalJobController(relay, "plugin-secret");

        assertThatThrownBy(() -> controller.relayOutbox("a".repeat(513)))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(relay);
    }
}
