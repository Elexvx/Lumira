package com.lumira.saas.modules.plugin.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PluginSemver {

    private static final Pattern SEMVER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+][0-9A-Za-z-.]+)?$");

    public void requireValid(String version, String fieldName) {
        if (!StringUtils.hasText(version) || !SEMVER_PATTERN.matcher(version).matches()) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, fieldName + " 必须符合 semver 语义版本格式");
        }
    }

    public boolean isCompatible(String currentVersion, String minRequiredVersion) {
        return compare(currentVersion, minRequiredVersion) >= 0;
    }

    public int compare(String left, String right) {
        int[] leftParts = parse(left);
        int[] rightParts = parse(right);
        for (int index = 0; index < 3; index++) {
            int compared = Integer.compare(leftParts[index], rightParts[index]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private int[] parse(String version) {
        requireValid(version, "版本号");
        Matcher matcher = SEMVER_PATTERN.matcher(version);
        matcher.matches();
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }
}
