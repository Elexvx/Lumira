package com.lumira.common.security;

public final class InternalServiceTokenPolicy {

    private InternalServiceTokenPolicy() {
    }

    public static String tokenForPath(
            String requestUri,
            String systemToken,
            String authToken,
            String authSystemToken,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String jobToken
    ) {
        return scopedTokenForPath(requestUri, systemToken, authToken, authSystemToken, fileToken, messageToken, paymentToken, pluginToken, null, jobToken);
    }

    public static String tokenForPath(
            String requestUri,
            String systemToken,
            String authToken,
            String authSystemToken,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String teamToken,
            String jobToken
    ) {
        return scopedTokenForPath(requestUri, systemToken, authToken, authSystemToken, fileToken, messageToken, paymentToken, pluginToken, teamToken, jobToken);
    }

    static String scopedTokenForPath(
            String requestUri,
            String systemToken,
            String authToken,
            String authSystemToken,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String teamToken,
            String jobToken
    ) {
        String path = requestUri == null ? "" : requestUri;
        if (isAuthScopedSystemPath(path)) {
            return authSystemToken;
        }
        if (isMessageScopedSystemPath(path)) {
            return messageToken;
        }
        if (path.startsWith("/internal/system/config/notification-runtime-values")
                || path.contains("/internal/system/config/notification-runtime-values")
                || path.startsWith("/internal/system/config/runtime/smtp")
                || path.contains("/internal/system/config/runtime/smtp")
                || path.startsWith("/internal/system/config/runtime/wechat-official")
                || path.contains("/internal/system/config/runtime/wechat-official")) {
            return messageToken;
        }
        if (path.startsWith("/internal/auth") || path.contains("/internal/auth/")) {
            return authToken;
        }
        if (path.startsWith("/internal/files") || path.contains("/internal/files/") || path.contains("/file/internal/")) {
            return fileToken;
        }
        if (path.startsWith("/internal/payment") || path.contains("/internal/payment/") || path.contains("/payment/internal/")) {
            return paymentToken;
        }
        if (path.startsWith("/internal/team") || path.contains("/internal/team/") || path.contains("/team/internal/")) {
            return teamToken;
        }
        if (path.contains("/message/internal/")) {
            return messageToken;
        }
        if (path.contains("/plugin/internal/")) {
            return pluginToken;
        }
        if (isPluginScopedSystemPath(path)) {
            return pluginToken;
        }
        if (path.startsWith("/internal/jobs") || path.contains("/internal/jobs/")) {
            return jobToken;
        }
        if (path.startsWith("/internal/system") || path.contains("/internal/system/")) {
            return systemToken;
        }
        return null;
    }

    private static boolean isMessageScopedSystemPath(String path) {
        return isMessageScopedSystemUserLookupPath(path)
                || isMessageScopedSystemRoleLookupPath(path)
                || matchesReadModelVersionRead(path, "message", "unread")
                || matchesReadModelVersionMutation(path, "message", "unread")
                || path.startsWith("/internal/system/audit/operation")
                || path.contains("/internal/system/audit/operation")
                || path.startsWith("/internal/system/permissions/role-snapshot")
                || path.contains("/internal/system/permissions/role-snapshot")
                || matchesInternalSystemUserSubPath(path, "/target-uuid")
                || path.startsWith("/internal/system/users/email-recipients")
                || path.contains("/internal/system/users/email-recipients")
                || path.startsWith("/internal/system/users/wechat-recipients")
                || path.contains("/internal/system/users/wechat-recipients")
                || matchesSystemRoleRecipientPath(path)
                || path.startsWith("/internal/system/platform/email-recipients")
                || path.contains("/internal/system/platform/email-recipients")
                || path.startsWith("/internal/system/platform/wechat-recipients")
                || path.contains("/internal/system/platform/wechat-recipients");
    }

    private static boolean isMessageScopedSystemUserLookupPath(String path) {
        return path.startsWith("/internal/system/users/identities-by-ids")
                || path.contains("/internal/system/users/identities-by-ids");
    }

    private static boolean isMessageScopedSystemRoleLookupPath(String path) {
        boolean roleUserLookupSuffix = path.endsWith("/identities") || path.contains("/identities?");
        return path.startsWith("/internal/system/roles/names-by-ids")
                || path.contains("/internal/system/roles/names-by-ids")
                || (path.startsWith("/internal/system/roles/") && roleUserLookupSuffix)
                || (path.contains("/internal/system/roles/") && roleUserLookupSuffix);
    }

    private static boolean matchesSystemRoleRecipientPath(String path) {
        boolean roleRecipientSuffix = path.contains("/email-recipients") || path.contains("/wechat-recipients");
        return (path.startsWith("/internal/system/roles/") && roleRecipientSuffix)
                || (path.contains("/internal/system/roles/") && roleRecipientSuffix);
    }

    private static boolean isPluginScopedSystemPath(String path) {
        return path.startsWith("/internal/system/permissions/plugin")
                || path.contains("/internal/system/permissions/plugin")
                || path.startsWith("/internal/system/permissions/invalidate")
                || path.contains("/internal/system/permissions/invalidate")
                || path.startsWith("/internal/system/menus/builtin")
                || path.contains("/internal/system/menus/builtin")
                || matchesReadModelVersionRead(path, "plugin", "bootstrap")
                || matchesReadModelVersionMutation(path, "plugin", "bootstrap")
                || matchesInternalSystemUserSubPath(path, "/email-available");
    }

    private static boolean isAuthScopedSystemPath(String path) {
        return path.startsWith("/internal/system/users/login/")
                || path.contains("/internal/system/users/login/")
                || matchesExactInternalSystemUserPath(path)
                || isAuthScopedSystemUserProfilePath(path)
                || path.startsWith("/internal/system/users/wechat-login")
                || path.contains("/internal/system/users/wechat-login")
                || path.startsWith("/internal/system/menus/ai-visible")
                || path.contains("/internal/system/menus/ai-visible")
                || path.startsWith("/internal/system/verification/")
                || path.contains("/internal/system/verification/")
                || path.startsWith("/internal/system/passkeys")
                || path.contains("/internal/system/passkeys")
                || path.startsWith("/internal/system/security/settings")
                || path.contains("/internal/system/security/settings")
                || path.startsWith("/internal/system/captcha/validate")
                || path.contains("/internal/system/captcha/validate")
                || path.startsWith("/internal/system/audit/login")
                || path.contains("/internal/system/audit/login")
                || matchesReadModelVersionRead(path, "platform", "public-bootstrap")
                || matchesReadModelVersionRead(path, "platform", "runtime-appearance")
                || path.startsWith("/internal/system/permissions/snapshot")
                || path.contains("/internal/system/permissions/snapshot");
    }

    private static boolean isAuthScopedSystemUserProfilePath(String path) {
        return matchesInternalSystemUserSubPath(path, "/profile")
                || matchesInternalSystemUserSubPath(path, "/requires-password-change");
    }

    private static boolean matchesInternalSystemUserSubPath(String path, String suffix) {
        return matchesInternalPathWithPrefix(path, "/internal/system/users/", suffix);
    }

    private static boolean matchesExactInternalSystemUserPath(String path) {
        int prefixIndex = path.indexOf("/internal/system/users/");
        if (prefixIndex < 0) {
            return false;
        }
        int idStart = prefixIndex + "/internal/system/users/".length();
        if (idStart >= path.length()) {
            return false;
        }
        int queryIndex = path.indexOf('?', idStart);
        int pathEnd = queryIndex >= 0 ? queryIndex : path.length();
        if (pathEnd <= idStart) {
            return false;
        }
        return path.substring(idStart, pathEnd).chars().allMatch(Character::isDigit);
    }

    private static boolean matchesInternalPathWithPrefix(String path, String prefix, String suffix) {
        int prefixIndex = path.indexOf(prefix);
        if (prefixIndex < 0) {
            return false;
        }
        int suffixIndex = path.indexOf(suffix, prefixIndex + prefix.length());
        if (suffixIndex < 0) {
            return false;
        }
        int suffixEnd = suffixIndex + suffix.length();
        return suffixEnd == path.length() || path.charAt(suffixEnd) == '?';
    }

    private static boolean matchesReadModelVersionMutation(String path, String contextName, String scope) {
        if (!isReadModelVersionMutationPath(path)) {
            return false;
        }
        return hasQueryParameter(path, "contextName", contextName)
                && hasQueryParameter(path, "scope", scope);
    }

    private static boolean matchesReadModelVersionRead(String path, String contextName, String scope) {
        if (!isReadModelVersionReadPath(path)) {
            return false;
        }
        return hasQueryParameter(path, "contextName", contextName)
                && hasQueryParameter(path, "scope", scope);
    }

    private static boolean isReadModelVersionMutationPath(String path) {
        return path.startsWith("/internal/system/read-model-version/bump?")
                || path.contains("/internal/system/read-model-version/bump?");
    }

    private static boolean isReadModelVersionReadPath(String path) {
        return path.startsWith("/internal/system/read-model-version?")
                || path.contains("/internal/system/read-model-version?");
    }

    private static boolean hasQueryParameter(String path, String key, String expectedValue) {
        int queryIndex = path.indexOf('?');
        if (queryIndex < 0 || queryIndex + 1 >= path.length()) {
            return false;
        }
        String query = path.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex == pair.length() - 1) {
                continue;
            }
            String parameterKey = pair.substring(0, equalsIndex);
            String parameterValue = pair.substring(equalsIndex + 1);
            if (key.equals(parameterKey) && expectedValue.equals(parameterValue)) {
                return true;
            }
        }
        return false;
    }
}
