package com.lumira.saas.modules.system.watermark.support;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WatermarkTemplateValidator {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)\\}\\}");
    private static final Set<String> ALLOWED_VARIABLES = Set.of(
            "username", "nickname", "mobile", "email", "realName", "userId"
    );

    private WatermarkTemplateValidator() {
    }

    public static void validate(List<String> lines) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            Matcher matcher = TOKEN_PATTERN.matcher(line);
            int cursor = 0;
            while (matcher.find()) {
                if (containsBrace(line.substring(cursor, matcher.start())) || !ALLOWED_VARIABLES.contains(matcher.group(1))) {
                    throw invalidTemplate();
                }
                cursor = matcher.end();
            }
            if (containsBrace(line.substring(cursor))) {
                throw invalidTemplate();
            }
        }
    }

    private static boolean containsBrace(String value) {
        return value.indexOf('{') >= 0 || value.indexOf('}') >= 0;
    }

    private static BizException invalidTemplate() {
        return new BizException(
                ErrorCode.VALIDATION_ERROR,
                "Watermark personalized text contains an unknown or malformed placeholder"
        );
    }
}
