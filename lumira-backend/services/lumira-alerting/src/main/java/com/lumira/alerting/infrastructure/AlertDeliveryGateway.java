package com.lumira.alerting.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.time.temporal.ChronoUnit;

@Component
public class AlertDeliveryGateway {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Set<String> WECOM_HOSTS = Set.of("qyapi.weixin.qq.com");
    private static final Set<String> FEISHU_HOSTS = Set.of("open.feishu.cn", "open.larksuite.com");
    private static final Set<String> DINGTALK_HOSTS = Set.of("oapi.dingtalk.com", "api.dingtalk.com");

    private final ObjectMapper objectMapper;
    private final ObjectProvider<JavaMailSender> systemMailSender;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final HttpClient httpClient;
    private final boolean allowPrivateTargets;
    private final Map<Long, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public AlertDeliveryGateway(
            ObjectMapper objectMapper,
            ObjectProvider<JavaMailSender> systemMailSender,
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${alerting.outbound.allow-private-targets:${ALERTING_ALLOW_PRIVATE_TARGETS:false}}") boolean allowPrivateTargets
    ) {
        this.objectMapper = objectMapper;
        this.systemMailSender = systemMailSender;
        this.redisProvider = redisProvider;
        this.allowPrivateTargets = allowPrivateTargets;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ProviderResult send(AlertingRepository.ChannelRecord channel, Map<String, Object> config,
                               String recipient, String eventType, String payloadJson) {
        return send(channel, config, null, recipient, eventType, payloadJson);
    }

    public ProviderResult send(AlertingRepository.ChannelRecord channel, Map<String, Object> config,
                               String memberType, String recipient, String eventType, String payloadJson) {
        try {
            ProviderResult limited = enforceRateLimit(channel.id(), config);
            if (limited != null) return limited;
            Map<String, Object> payload = objectMapper.readValue(payloadJson, MAP_TYPE);
            return switch (channel.type()) {
                case "WECOM_WEBHOOK" -> sendWeComWebhook(config, payload);
                case "FEISHU_WEBHOOK" -> sendFeishuWebhook(config, payload);
                case "DINGTALK_WEBHOOK" -> sendDingTalkWebhook(config, payload);
                case "WECOM_APP" -> sendWeComApp(config, recipient, payload);
                case "FEISHU_APP" -> sendFeishuApp(config, memberType, recipient, payload);
                case "DINGTALK_APP" -> sendDingTalkApp(config, memberType, recipient, payload);
                case "EMAIL_SYSTEM_SMTP" -> sendSystemEmail(config, recipient, eventType, payload);
                case "EMAIL_CUSTOM_SMTP" -> sendCustomEmail(config, recipient, eventType, payload);
                default -> throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported alert channel type: " + channel.type());
            };
        } catch (BizException exception) {
            return new ProviderResult(false, false, null, null, safeMessage(exception));
        } catch (ProviderException exception) {
            return new ProviderResult(false, exception.retryable, null, null, safeMessage(exception));
        } catch (Exception exception) {
            return new ProviderResult(false, true, null, null, safeMessage(exception));
        }
    }

    private ProviderResult enforceRateLimit(long channelId, Map<String, Object> config) {
        int limit = intValue(config, "rateLimitPerMinute", 60);
        if (limit < 1 || limit > 600) {
            throw new BizException(ErrorCode.BAD_REQUEST, "rateLimitPerMinute must be between 1 and 600");
        }
        long minute = Instant.now().getEpochSecond() / 60;
        RateWindow window = rateWindows.compute(channelId, (ignored, current) -> {
            if (current == null || current.minute != minute) return new RateWindow(minute, 1);
            return new RateWindow(minute, current.count + 1);
        });
        return window.count > limit
                ? new ProviderResult(false, true, null, null, "Local channel rate limit exceeded")
                : null;
    }

    public ProviderResult test(AlertingRepository.ChannelRecord channel, Map<String, Object> config) {
        String recipient = string(config, "testRecipient", false);
        if (recipient == null) recipient = channel.type().contains("WEBHOOK") ? "webhook" : null;
        if (recipient == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "testRecipient is required for this channel type");
        }
        Map<String, Object> payload = Map.of(
                "title", "Lumira 告警渠道测试",
                "severity", "INFO",
                "summary", "这是一条连接测试消息。收到后说明该渠道实例配置可用。",
                "detailsUrl", string(config, "detailsBaseUrl", false) == null ? "" : string(config, "detailsBaseUrl", false)
        );
        try {
            return send(channel, config, recipient, "TEST", objectMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create test notification", exception);
        }
    }

    public List<ExternalDirectoryUser> directoryUsers(String channelType, Map<String, Object> config) {
        try {
            return switch (channelType) {
                case "WECOM_APP" -> weComDirectoryUsers(config);
                case "FEISHU_APP" -> feishuDirectoryUsers(config);
                case "DINGTALK_APP" -> dingTalkDirectoryUsers(config);
                default -> throw new BizException(ErrorCode.BAD_REQUEST, "Directory sync requires an enterprise app channel");
            };
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Enterprise directory sync failed: " + safeMessage(exception));
        }
    }

    public void validateConfiguration(String type, Map<String, Object> config) {
        switch (type) {
            case "WECOM_WEBHOOK" -> validateWebhook(string(config, "webhookUrl", true), WECOM_HOSTS);
            case "FEISHU_WEBHOOK" -> validateWebhook(string(config, "webhookUrl", true), FEISHU_HOSTS);
            case "DINGTALK_WEBHOOK" -> validateWebhook(string(config, "webhookUrl", true), DINGTALK_HOSTS);
            case "WECOM_APP" -> {
                string(config, "corpId", true); string(config, "agentId", true); string(config, "secret", true);
            }
            case "FEISHU_APP" -> {
                string(config, "appId", true); string(config, "appSecret", true);
            }
            case "DINGTALK_APP" -> {
                string(config, "clientId", true); string(config, "clientSecret", true); string(config, "robotCode", true);
            }
            case "EMAIL_SYSTEM_SMTP" -> string(config, "from", true);
            case "EMAIL_CUSTOM_SMTP" -> {
                String host = string(config, "host", true);
                validateResolvedHost(host);
                int port = intValue(config, "port", 0);
                if (port < 1 || port > 65535) throw new BizException(ErrorCode.BAD_REQUEST, "Invalid SMTP port");
                string(config, "username", true); string(config, "password", true); string(config, "from", true);
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported alert channel type");
        }
    }

    private ProviderResult sendWeComWebhook(Map<String, Object> config, Map<String, Object> payload) throws Exception {
        URI uri = validateWebhook(string(config, "webhookUrl", true), WECOM_HOSTS);
        Map<String, Object> body = Map.of("msgtype", "markdown", "markdown", Map.of("content", markdown(payload)));
        return postJson(uri, body, Map.of(), "errcode", 0);
    }

    private ProviderResult sendFeishuWebhook(Map<String, Object> config, Map<String, Object> payload) throws Exception {
        URI uri = validateWebhook(string(config, "webhookUrl", true), FEISHU_HOSTS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "interactive");
        body.put("card", feishuCard(payload));
        String secret = string(config, "signSecret", false);
        if (secret != null) {
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            body.put("timestamp", timestamp);
            body.put("sign", feishuSignature(timestamp, secret));
        }
        return postJson(uri, body, Map.of(), "code", 0);
    }

    private ProviderResult sendDingTalkWebhook(Map<String, Object> config, Map<String, Object> payload) throws Exception {
        URI uri = validateWebhook(string(config, "webhookUrl", true), DINGTALK_HOSTS);
        String secret = string(config, "signSecret", false);
        if (secret != null) uri = appendDingTalkSignature(uri, secret);
        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("title", text(payload, "title"), "text", markdown(payload))
        );
        return postJson(uri, body, Map.of(), "errcode", 0);
    }

    private ProviderResult sendWeComApp(Map<String, Object> config, String recipient, Map<String, Object> payload) throws Exception {
        String tenant = string(config, "corpId", true);
        CheckedSupplier supplier = () -> queryToken(
                URI.create("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + encode(tenant)
                        + "&corpsecret=" + encode(string(config, "secret", true))),
                Map.of(), null, "access_token");
        String token = cachedToken("wecom", tenant, supplier);
        Map<String, Object> body = Map.of(
                "touser", recipient,
                "msgtype", "markdown",
                "agentid", Long.parseLong(string(config, "agentId", true)),
                "markdown", Map.of("content", markdown(payload)),
                "enable_duplicate_check", 1,
                "duplicate_check_interval", 1800
        );
        ProviderResult result = postJson(
                URI.create("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + encode(token)),
                body, Map.of(), "errcode", 0);
        if (isAuthenticationRejection(result, Set.of(40014, 42001))) {
            evictCachedToken("wecom", tenant);
            token = cachedToken("wecom", tenant, supplier);
            result = postJson(
                    URI.create("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + encode(token)),
                    body, Map.of(), "errcode", 0);
        }
        return result;
    }

    private ProviderResult sendFeishuApp(Map<String, Object> config, String memberType, String recipient,
                                         Map<String, Object> payload) throws Exception {
        String tenant = string(config, "appId", true);
        CheckedSupplier supplier = () -> queryToken(
                URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"),
                Map.of("app_id", tenant, "app_secret", string(config, "appSecret", true)),
                "tenant_access_token", "tenant_access_token"
        );
        String token = cachedToken("feishu", tenant, supplier);
        String receiveType = "CHAT".equals(memberType) ? "chat_id" : string(config, "receiveIdType", false);
        if (receiveType == null) receiveType = "user_id";
        URI uri = URI.create("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + encode(receiveType));
        Map<String, Object> body = Map.of(
                "receive_id", recipient,
                "msg_type", "interactive",
                "content", objectMapper.writeValueAsString(feishuCard(payload))
        );
        ProviderResult result = postJson(uri, body, Map.of("Authorization", "Bearer " + token), "code", 0);
        if (isAuthenticationRejection(result, Set.of(99991663, 99991664))) {
            evictCachedToken("feishu", tenant);
            token = cachedToken("feishu", tenant, supplier);
            result = postJson(uri, body, Map.of("Authorization", "Bearer " + token), "code", 0);
        }
        return result;
    }

    private ProviderResult sendDingTalkApp(Map<String, Object> config, String memberType, String recipient,
                                           Map<String, Object> payload) throws Exception {
        String tenant = string(config, "clientId", true);
        CheckedSupplier supplier = () -> queryToken(
                URI.create("https://api.dingtalk.com/v1.0/oauth2/accessToken"),
                Map.of("appKey", tenant, "appSecret", string(config, "clientSecret", true)),
                "accessToken", "accessToken"
        );
        String token = cachedToken("dingtalk", tenant, supplier);
        boolean group = memberType == null || "CHAT".equals(memberType);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("robotCode", string(config, "robotCode", true));
        if (group) body.put("openConversationId", recipient);
        else body.put("userIds", List.of(recipient));
        body.put("msgKey", "sampleMarkdown");
        body.put("msgParam", objectMapper.writeValueAsString(
                Map.of("title", text(payload, "title"), "text", markdown(payload))));
        URI uri = URI.create(group
                ? "https://api.dingtalk.com/v1.0/robot/groupMessages/send"
                : "https://api.dingtalk.com/v1.0/robot/oToMessages/batchSend");
        ProviderResult result = postJson(uri, body,
                Map.of("x-acs-dingtalk-access-token", token), null, 0);
        if (isAuthenticationRejection(result, Set.of())) {
            evictCachedToken("dingtalk", tenant);
            token = cachedToken("dingtalk", tenant, supplier);
            result = postJson(uri, body, Map.of("x-acs-dingtalk-access-token", token), null, 0);
        }
        return result;
    }

    private ProviderResult sendSystemEmail(Map<String, Object> config, String recipient, String eventType,
                                           Map<String, Object> payload) throws Exception {
        JavaMailSender sender = systemMailSender.getIfAvailable();
        if (sender == null) throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "System SMTP is not configured");
        sendMail(sender, string(config, "from", true), recipient, eventType, payload);
        return new ProviderResult(true, false, null, "SMTP accepted", null);
    }

    private ProviderResult sendCustomEmail(Map<String, Object> config, String recipient, String eventType,
                                           Map<String, Object> payload) throws Exception {
        String host = string(config, "host", true);
        validateResolvedHost(host);
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(intValue(config, "port", 465));
        sender.setUsername(string(config, "username", true));
        sender.setPassword(string(config, "password", true));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        boolean ssl = boolValue(config, "ssl", sender.getPort() == 465);
        boolean startTls = boolValue(config, "startTls", sender.getPort() == 587);
        properties.put("mail.smtp.ssl.enable", Boolean.toString(ssl));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.put("mail.smtp.starttls.required", Boolean.toString(startTls));
        sendMail(sender, string(config, "from", true), recipient, eventType, payload);
        return new ProviderResult(true, false, null, "SMTP accepted", null);
    }

    private void sendMail(JavaMailSender sender, String from, String recipient, String eventType,
                          Map<String, Object> payload) throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(recipient);
        helper.setSubject("[Lumira][" + eventType + "][" + text(payload, "severity") + "] " + text(payload, "title"));
        helper.setText(markdown(payload), "<h2>" + html(text(payload, "title")) + "</h2><p>"
                + html(text(payload, "summary")) + "</p><p><a href=\"" + html(text(payload, "detailsUrl"))
                + "\">查看告警详情</a></p>");
        sender.send(message);
    }

    private String queryToken(URI uri, Map<String, Object> body, String tokenFieldHint, String tokenField) throws Exception {
        HostPolicy policy = policyFor(uri.getHost());
        validateUri(uri, policy.allowedHosts());
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10));
        if (body.isEmpty()) request.GET();
        else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ProviderException(response.statusCode() == 429 || response.statusCode() >= 500, "Provider token request failed: HTTP " + response.statusCode());
        }
        JsonNode json = objectMapper.readTree(response.body());
        JsonNode token = json.path(tokenField);
        if (token.isMissingNode() || token.asText().isBlank()) {
            throw new ProviderException(false, "Provider token response did not contain " + (tokenFieldHint == null ? tokenField : tokenFieldHint));
        }
        return token.asText();
    }

    private List<ExternalDirectoryUser> weComDirectoryUsers(Map<String, Object> config) throws Exception {
        String tenant = string(config, "corpId", true);
        String token = cachedToken("wecom", tenant, () -> queryToken(
                URI.create("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + encode(tenant)
                        + "&corpsecret=" + encode(string(config, "secret", true))), Map.of(), null, "access_token"));
        JsonNode root = getJson(URI.create("https://qyapi.weixin.qq.com/cgi-bin/user/list?department_id=1&fetch_child=1&access_token=" + encode(token)), Map.of());
        ensureProviderCode(root, "errcode");
        List<ExternalDirectoryUser> users = new ArrayList<>();
        for (JsonNode user : root.path("userlist")) {
            users.add(new ExternalDirectoryUser(user.path("userid").asText(), user.path("name").asText(),
                    user.path("email").asText(null), user.path("mobile").asText(null)));
        }
        return users;
    }

    private List<ExternalDirectoryUser> feishuDirectoryUsers(Map<String, Object> config) throws Exception {
        String tenant = string(config, "appId", true);
        String token = cachedToken("feishu", tenant, () -> queryToken(
                URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"),
                Map.of("app_id", tenant, "app_secret", string(config, "appSecret", true)),
                "tenant_access_token", "tenant_access_token"));
        List<ExternalDirectoryUser> users = new ArrayList<>();
        String pageToken = null;
        do {
            String suffix = pageToken == null ? "" : "&page_token=" + encode(pageToken);
            JsonNode root = getJson(URI.create("https://open.feishu.cn/open-apis/contact/v3/users/find_by_department"
                    + "?department_id=0&department_id_type=open_department_id&page_size=50" + suffix),
                    Map.of("Authorization", "Bearer " + token));
            ensureProviderCode(root, "code");
            for (JsonNode user : root.path("data").path("items")) {
                users.add(new ExternalDirectoryUser(user.path("user_id").asText(), user.path("name").asText(),
                        user.path("email").asText(null), user.path("mobile").asText(null)));
            }
            pageToken = root.path("data").path("has_more").asBoolean(false)
                    ? root.path("data").path("page_token").asText(null) : null;
        } while (pageToken != null && !pageToken.isBlank() && users.size() < 10000);
        return users;
    }

    private List<ExternalDirectoryUser> dingTalkDirectoryUsers(Map<String, Object> config) throws Exception {
        String tenant = string(config, "clientId", true);
        String token = cachedToken("dingtalk", tenant, () -> queryToken(
                URI.create("https://api.dingtalk.com/v1.0/oauth2/accessToken"),
                Map.of("appKey", tenant, "appSecret", string(config, "clientSecret", true)),
                "accessToken", "accessToken"));
        List<ExternalDirectoryUser> users = new ArrayList<>();
        String cursor = "0";
        do {
            JsonNode root = getJson(URI.create("https://api.dingtalk.com/v1.0/contact/users?deptId=1&size=100&cursor=" + encode(cursor)),
                    Map.of("x-acs-dingtalk-access-token", token));
            for (JsonNode user : root.path("list")) {
                users.add(new ExternalDirectoryUser(user.path("userid").asText(user.path("userId").asText()),
                        user.path("name").asText(), user.path("email").asText(null), user.path("mobile").asText(null)));
            }
            cursor = root.path("hasMore").asBoolean(false) ? root.path("nextCursor").asText(null) : null;
        } while (cursor != null && !cursor.isBlank() && users.size() < 10000);
        return users;
    }

    private JsonNode getJson(URI uri, Map<String, String> headers) throws Exception {
        validateUri(uri, policyFor(uri.getHost()).allowedHosts());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET();
        headers.forEach(builder::header);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ProviderException(response.statusCode() == 429 || response.statusCode() >= 500,
                    "Directory provider returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private static void ensureProviderCode(JsonNode root, String field) throws ProviderException {
        if (root.has(field) && root.path(field).asInt(-1) != 0) {
            throw new ProviderException(false, "Directory provider rejected the request with code " + root.path(field).asInt());
        }
    }

    private String cachedToken(String provider, String tenantKey, CheckedSupplier supplier) throws Exception {
        String cacheKey = tokenCacheKey(provider, tenantKey);
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) return cached;
        }
        String token = supplier.get();
        if (redis != null) redis.opsForValue().set(cacheKey, token, Duration.of(90, ChronoUnit.MINUTES));
        return token;
    }

    private void evictCachedToken(String provider, String tenantKey) throws Exception {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) redis.delete(tokenCacheKey(provider, tenantKey));
    }

    private String tokenCacheKey(String provider, String tenantKey) throws Exception {
        return "lumira:alerting:token:" + provider + ":" + sha256(tenantKey);
    }

    private boolean isAuthenticationRejection(ProviderResult result, Set<Integer> providerCodes) {
        if (result == null || result.success()) return false;
        String error = result.error() == null ? "" : result.error().toLowerCase(Locale.ROOT);
        if (error.contains("http 401") || error.contains("http 403")) return true;
        String summary = result.responseSummary();
        if (summary == null || summary.isBlank()) return false;
        try {
            JsonNode json = objectMapper.readTree(summary);
            int code = json.has("errcode") ? json.path("errcode").asInt(Integer.MIN_VALUE)
                    : json.path("code").asInt(Integer.MIN_VALUE);
            String textualCode = json.path("code").asText("").toLowerCase(Locale.ROOT);
            return providerCodes.contains(code)
                    || textualCode.contains("invalidauthentication")
                    || textualCode.contains("accesstokeninvalid");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private ProviderResult postJson(URI uri, Map<String, Object> body, Map<String, String> headers,
                                    String providerCodeField, int providerSuccessCode) throws Exception {
        validateUri(uri, policyFor(uri.getHost()).allowedHosts());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        headers.forEach(builder::header);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String summary = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new ProviderResult(false, response.statusCode() == 429 || response.statusCode() >= 500,
                    null, summary, "Provider returned HTTP " + response.statusCode());
        }
        JsonNode json = summary.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(summary);
        if (providerCodeField != null && json.has(providerCodeField) && json.path(providerCodeField).asInt(-1) != providerSuccessCode) {
            int code = json.path(providerCodeField).asInt(-1);
            return new ProviderResult(false, code == 429 || code >= 500, null, summary, "Provider rejected message with code " + code);
        }
        String messageId = firstText(json, List.of("message_id", "messageId", "msgid", "processQueryKey"));
        return new ProviderResult(true, false, messageId, summary, null);
    }

    private URI validateWebhook(String value, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid webhook URL");
        }
        validateUri(uri, allowedHosts);
        return uri;
    }

    private void validateUri(URI uri, Set<String> allowedHosts) {
        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (host == null || scheme == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid outbound URL");
        }
        if (!"https".equalsIgnoreCase(scheme) && !(allowPrivateTargets && "http".equalsIgnoreCase(scheme))) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Outbound alert URLs must use HTTPS");
        }
        if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT)) && !allowPrivateTargets) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Outbound host is not in the provider allowlist");
        }
        validateResolvedHost(host);
    }

    private void validateResolvedHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!allowPrivateTargets && (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || isIpv6UniqueLocal(address.getAddress()))) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "Private or local outbound targets are blocked");
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Outbound host cannot be resolved");
        }
    }

    private HostPolicy policyFor(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (WECOM_HOSTS.contains(normalized)) return new HostPolicy(WECOM_HOSTS);
        if (FEISHU_HOSTS.contains(normalized)) return new HostPolicy(FEISHU_HOSTS);
        if (DINGTALK_HOSTS.contains(normalized)) return new HostPolicy(DINGTALK_HOSTS);
        return new HostPolicy(Set.of(normalized));
    }

    private static String markdown(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append("### ").append(text(payload, "title")).append("\n");
        text.append("**级别：** ").append(text(payload, "severity")).append("\n\n");
        text.append(text(payload, "summary"));
        String details = text(payload, "detailsUrl");
        if (!details.isBlank()) text.append("\n\n[查看告警详情](").append(details).append(")");
        return text.toString();
    }

    private static Map<String, Object> feishuCard(Map<String, Object> payload) {
        return Map.of(
                "header", Map.of("title", Map.of("tag", "plain_text", "content", text(payload, "title"))),
                "elements", List.of(
                        Map.of("tag", "markdown", "content", markdown(payload)),
                        Map.of("tag", "note", "elements", List.of(Map.of("tag", "plain_text", "content", "请在 Lumira 后台确认或静默告警")))
                )
        );
    }

    private static URI appendDingTalkSignature(URI uri, String secret) throws Exception {
        long timestamp = System.currentTimeMillis();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = Base64.getEncoder().encodeToString(mac.doFinal((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8)));
        String separator = uri.getQuery() == null ? "?" : "&";
        return URI.create(uri + separator + "timestamp=" + timestamp + "&sign=" + encode(sign));
    }

    private static String feishuSignature(String timestamp, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
    }

    private static String firstText(JsonNode json, List<String> fields) {
        for (String field : fields) if (json.path(field).isTextual()) return json.path(field).asText();
        return null;
    }

    private static String string(Map<String, Object> config, String key, boolean required) {
        Object value = config == null ? null : config.get(key);
        String text = value == null ? null : value.toString().trim();
        if (text != null && text.isBlank()) text = null;
        if (required && text == null) throw new BizException(ErrorCode.BAD_REQUEST, key + " is required");
        return text;
    }

    private static int intValue(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException exception) { throw new BizException(ErrorCode.BAD_REQUEST, key + " must be a number"); }
    }

    private static boolean boolValue(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private static String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : value.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String html(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static boolean isIpv6UniqueLocal(byte[] address) {
        return address != null && address.length == 16 && (address[0] & 0xfe) == 0xfc;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record ProviderResult(boolean success, boolean retryable, String providerMessageId,
                                 String responseSummary, String error) { }

    public record ExternalDirectoryUser(String providerUserId, String displayName, String email, String mobile) { }

    @FunctionalInterface
    private interface CheckedSupplier { String get() throws Exception; }

    private record HostPolicy(Set<String> allowedHosts) { }

    private record RateWindow(long minute, int count) { }

    private static final class ProviderException extends Exception {
        private final boolean retryable;

        private ProviderException(boolean retryable, String message) {
            super(message);
            this.retryable = retryable;
        }
    }
}
