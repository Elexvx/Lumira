package com.lumira.saas.modules.system.watermark.support;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatermarkTemplateValidatorTest {

    @Test
    void acceptsWhitelistedVariablesAndLiteralSeparators() {
        assertThatCode(() -> WatermarkTemplateValidator.validate(List.of(
                "用户：{{username}} | 手机：{{mobile}} | ID：{{userId}}",
                "固定前缀/{{realName}}/固定后缀"
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownOrMalformedVariables() {
        assertThatThrownBy(() -> WatermarkTemplateValidator.validate(List.of("{{phone}}")))
                .hasMessageContaining("unknown or malformed placeholder");
        assertThatThrownBy(() -> WatermarkTemplateValidator.validate(List.of("{{username}")))
                .hasMessageContaining("unknown or malformed placeholder");
        assertThatThrownBy(() -> WatermarkTemplateValidator.validate(List.of("{username}")))
                .hasMessageContaining("unknown or malformed placeholder");
    }
}
