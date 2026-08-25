package com.lumira.alerting.app;

import com.lumira.alerting.infrastructure.AlertDeliveryGateway;
import com.lumira.alerting.infrastructure.AlertingRepository;
import com.lumira.alerting.infrastructure.AlertingSecretCrypto;
import com.lumira.alerting.model.AlertingModels;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;

@Service
public class AlertingAppService {
    public static final Set<String> CHANNEL_TYPES = Set.of(
            "WECOM_WEBHOOK", "WECOM_APP", "FEISHU_WEBHOOK", "FEISHU_APP",
            "DINGTALK_WEBHOOK", "DINGTALK_APP", "EMAIL_SYSTEM_SMTP", "EMAIL_CUSTOM_SMTP"
    );
    public static final Set<String> MEMBER_TYPES = Set.of("USER", "EMAIL", "EXTERNAL_USER", "CHAT", "WEBHOOK");
    public static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "CRITICAL");
    public static final Set<String> COMPARATORS = Set.of("GT", "GTE", "LT", "LTE", "EQ", "NE");

    public static final List<AlertingModels.CatalogSignal> SIGNALS = List.of(
            signal("service.up", "服务可用性", "PROMETHEUS", "bool", "服务实例健康状态"),
            signal("http.5xx.rate", "HTTP 5xx 错误率", "PROMETHEUS", "%", "5 分钟服务端错误率"),
            signal("http.p95", "HTTP P95 延迟", "PROMETHEUS", "s", "5 分钟请求 P95 延迟"),
            signal("jvm.heap.usage", "JVM 堆内存使用率", "PROMETHEUS", "%", "JVM 堆内存使用率"),
            signal("mysql.up", "MySQL 可用性", "PROMETHEUS", "bool", "MySQL exporter 健康状态"),
            signal("mysql.connections", "MySQL 连接使用率", "PROMETHEUS", "%", "当前连接与最大连接比率"),
            signal("redis.up", "Redis 可用性", "PROMETHEUS", "bool", "Redis exporter 健康状态"),
            signal("outbox.backlog", "事件 Outbox 积压", "PROMETHEUS", "count", "待处理平台事件数量"),
            signal("alert.delivery.backlog", "告警投递积压", "PROMETHEUS", "count", "待投递告警数量"),
            signal("backup.age", "最近备份时间", "PROMETHEUS", "s", "距最近成功备份的秒数"),
            signal("host.disk.usage", "主机磁盘使用率", "PROMETHEUS", "%", "部署主机磁盘使用率"),
            signal("business.payment.paid", "支付成功事件", "BUSINESS_EVENT", "count", "时间窗口内支付成功事件数量"),
            signal("business.registration.submitted", "报名提交事件", "BUSINESS_EVENT", "count", "时间窗口内报名提交事件数量"),
            signal("business.review.completed", "评审完成事件", "BUSINESS_EVENT", "count", "时间窗口内评审完成事件数量"),
            signal("business.file.scan.failed", "文件扫描失败事件", "BUSINESS_EVENT", "count", "时间窗口内文件扫描失败事件数量")
    );

    private final AlertingRepository repository;
    private final AlertingSecretCrypto crypto;
    private final AlertDeliveryGateway gateway;

    public AlertingAppService(AlertingRepository repository, AlertingSecretCrypto crypto, AlertDeliveryGateway gateway) {
        this.repository = repository;
        this.crypto = crypto;
        this.gateway = gateway;
    }

    public List<AlertingModels.CatalogSignal> catalog() {
        requireEnabled();
        return SIGNALS;
    }

    public List<AlertingModels.ChannelView> channels() {
        requireEnabled();
        return repository.listChannels().stream().map(this::toChannelView).toList();
    }

    @Transactional
    public AlertingModels.ChannelView saveChannel(Long id, AlertingModels.ChannelRequest request, CurrentUser user) {
        requireEnabled();
        String type = upper(request.type());
        if (!CHANNEL_TYPES.contains(type)) throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported channel type");
        Map<String, Object> config = mergeExistingSecrets(id, type, request.config());
        gateway.validateConfiguration(type, config);
        String encrypted = crypto.encrypt(config);
        String fingerprint = crypto.fingerprint(config);
        long operatorId = user.getUserId();
        long channelId;
        if (id == null) {
            channelId = repository.insertChannel(clean(request.name()), type, request.enabled(), encrypted, fingerprint, operatorId);
        } else {
            if (request.version() == null) throw new BizException(ErrorCode.BAD_REQUEST, "Channel version is required");
            repository.updateChannel(id, request.version(), clean(request.name()), type, request.enabled(), encrypted, fingerprint, operatorId);
            channelId = id;
        }
        return toChannelView(repository.findChannel(channelId).orElseThrow());
    }

    public void deleteChannel(long id, CurrentUser user) {
        requireEnabled();
        repository.deleteChannel(id, user.getUserId());
    }

    public AlertDeliveryGateway.ProviderResult testChannel(long id) {
        requireEnabled();
        AlertingRepository.ChannelRecord channel = repository.findChannel(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Alert channel not found"));
        Map<String, Object> config = crypto.decrypt(channel.encryptedConfig());
        AlertDeliveryGateway.ProviderResult result = gateway.test(channel, config);
        repository.recordChannelTest(id, result.success() ? "SUCCESS" : "FAILED", result.error());
        return result;
    }

    public List<AlertingModels.ContactGroupView> contactGroups() {
        requireEnabled();
        return repository.listContactGroups();
    }

    @Transactional
    public AlertingModels.ContactGroupView saveContactGroup(Long id, AlertingModels.ContactGroupRequest request, CurrentUser user) {
        requireEnabled();
        if (request.members().isEmpty()) throw new BizException(ErrorCode.BAD_REQUEST, "A contact group must contain at least one member");
        for (AlertingModels.ContactMemberRequest member : request.members()) {
            if (!MEMBER_TYPES.contains(upper(member.memberType()))) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported contact member type");
            }
            AlertingRepository.ChannelRecord channel = repository.findChannel(member.channelId())
                    .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST, "Contact member references an unknown channel"));
            validateMemberCompatibility(channel.type(), upper(member.memberType()), member.targetIdentifier());
        }
        List<AlertingModels.ContactMemberRequest> normalized = request.members().stream()
                .map(member -> new AlertingModels.ContactMemberRequest(
                        member.channelId(), upper(member.memberType()), clean(member.targetIdentifier()),
                        cleanNullable(member.displayName()), member.enabled()
                )).toList();
        long groupId = repository.saveContactGroup(id, request.version(), clean(request.name()), request.enabled(), normalized, user.getUserId());
        return repository.findContactGroup(groupId).orElseThrow();
    }

    public void deleteContactGroup(long id, CurrentUser user) {
        requireEnabled();
        repository.deleteContactGroup(id, user.getUserId());
    }

    public List<AlertingModels.RuleView> rules() {
        requireEnabled();
        return repository.listRules();
    }

    public AlertingModels.RuleView saveRule(Long id, AlertingModels.RuleRequest request, CurrentUser user) {
        requireEnabled();
        AlertingModels.RuleRequest normalized = normalizeRule(request);
        repository.findContactGroup(normalized.contactGroupId())
                .filter(AlertingModels.ContactGroupView::enabled)
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST, "Enabled contact group is required"));
        long ruleId;
        if (id == null) {
            ruleId = repository.insertRule(normalized, user.getUserId());
        } else {
            repository.updateRule(id, normalized, user.getUserId());
            ruleId = id;
        }
        return repository.findRule(ruleId).orElseThrow();
    }

    public void deleteRule(long id, CurrentUser user) {
        requireEnabled();
        repository.deleteRule(id, user.getUserId());
    }

    public Map<String, Object> previewRule(AlertingModels.RuleRequest request) {
        requireEnabled();
        AlertingModels.RuleRequest normalized = normalizeRule(request);
        return Map.of(
                "valid", true,
                "sourceType", normalized.sourceType(),
                "signalKey", normalized.signalKey(),
                "description", "当 " + normalized.signalKey() + " " + normalized.comparator() + " " + normalized.threshold()
                        + " 持续 " + normalized.pendingSeconds() + " 秒后触发；连续 2 次恢复后关闭"
        );
    }

    public List<AlertingModels.AlertInstanceView> instances(String status) {
        requireEnabled();
        String normalized = status == null || status.isBlank() ? null : upper(status);
        return repository.listInstances(normalized);
    }

    public void acknowledge(long id, long version, CurrentUser user) {
        requireEnabled();
        repository.acknowledge(id, version, user.getUserId());
    }

    public List<AlertingModels.SilenceView> silences() {
        requireEnabled();
        return repository.listSilences();
    }

    public AlertingModels.SilenceView saveSilence(Long id, AlertingModels.SilenceRequest request, CurrentUser user) {
        requireEnabled();
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Silence end time must be after start time");
        }
        if (request.endsAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Silence end time must be in the future");
        }
        long silenceId = repository.saveSilence(id, request, user.getUserId());
        return repository.listSilences().stream().filter(item -> item.id() == silenceId).findFirst().orElseThrow();
    }

    public void deleteSilence(long id, CurrentUser user) {
        requireEnabled();
        repository.deleteSilence(id, user.getUserId());
    }

    public List<AlertingModels.DeliveryView> deliveries(String status) {
        requireEnabled();
        return repository.listDeliveries(status == null || status.isBlank() ? null : upper(status));
    }

    public void retryDelivery(long id) {
        requireEnabled();
        repository.retryDelivery(id);
    }

    public List<AlertingModels.DirectoryMappingView> directoryMappings(Long channelId) {
        requireEnabled();
        return repository.listDirectoryMappings(channelId);
    }

    public AlertingModels.DirectoryMappingView saveDirectoryMapping(AlertingModels.DirectoryMappingRequest request, CurrentUser user) {
        requireEnabled();
        AlertingRepository.ChannelRecord channel = repository.findChannel(request.channelId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Alert channel not found"));
        if (!Set.of("WECOM_APP", "FEISHU_APP", "DINGTALK_APP").contains(channel.type())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Directory mappings are only supported for enterprise app channels");
        }
        long id = repository.saveDirectoryMapping(request, user.getUserId());
        return repository.listDirectoryMappings(request.channelId()).stream().filter(item -> item.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String, Object> syncDirectory(long channelId, CurrentUser user) {
        requireEnabled();
        AlertingRepository.ChannelRecord channel = repository.findChannel(channelId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Alert channel not found"));
        if (!Set.of("WECOM_APP", "FEISHU_APP", "DINGTALK_APP").contains(channel.type())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Directory sync requires an enterprise app channel");
        }
        Map<String, Object> config = crypto.decrypt(channel.encryptedConfig());
        List<AlertDeliveryGateway.ExternalDirectoryUser> externalUsers = gateway.directoryUsers(channel.type(), config).stream()
                .filter(item -> item.providerUserId() != null && !item.providerUserId().isBlank())
                .toList();
        Map<String, List<AlertDeliveryGateway.ExternalDirectoryUser>> byEmail = new HashMap<>();
        Map<String, List<AlertDeliveryGateway.ExternalDirectoryUser>> byPhone = new HashMap<>();
        for (AlertDeliveryGateway.ExternalDirectoryUser external : externalUsers) {
            String email = normalizeEmail(external.email());
            String phone = normalizePhone(external.mobile());
            if (email != null) byEmail.computeIfAbsent(email, ignored -> new ArrayList<>()).add(external);
            if (phone != null) byPhone.computeIfAbsent(phone, ignored -> new ArrayList<>()).add(external);
        }
        List<AlertingRepository.AutomaticMapping> mappings = new ArrayList<>();
        int matched = 0;
        int ambiguous = 0;
        int unmatched = 0;
        for (AlertingRepository.LocalDirectoryUser local : repository.localDirectoryUsers()) {
            Match match = match(local, byEmail, byPhone);
            mappings.add(new AlertingRepository.AutomaticMapping(
                    local.userId(), local.userUuid(), match.user() == null ? "" : match.user().providerUserId(),
                    match.user() == null ? null : match.user().displayName(), match.source(), match.status()
            ));
            if ("MATCHED".equals(match.status())) matched++;
            else if ("AMBIGUOUS".equals(match.status())) ambiguous++;
            else unmatched++;
        }
        repository.replaceAutomaticDirectoryMappings(channelId, mappings, user.getUserId());
        return Map.of(
                "channelId", channelId,
                "providerUsers", externalUsers.size(),
                "localUsers", mappings.size(),
                "matched", matched,
                "ambiguous", ambiguous,
                "unmatched", unmatched
        );
    }

    public AlertingModels.HealthView health() {
        return repository.health();
    }

    public void requireEnabled() {
        if (!repository.pluginEnabled()) {
            throw new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "Built-in alerting plugin is not enabled");
        }
    }

    private AlertingModels.RuleRequest normalizeRule(AlertingModels.RuleRequest request) {
        String source = upper(request.sourceType());
        String comparator = upper(request.comparator());
        String severity = upper(request.severity());
        if (!Set.of("PROMETHEUS", "BUSINESS_EVENT").contains(source)) throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported rule source");
        if (!COMPARATORS.contains(comparator)) throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported comparator");
        if (!SEVERITIES.contains(severity)) throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported severity");
        AlertingModels.CatalogSignal signal = SIGNALS.stream().filter(item -> item.key().equals(request.signalKey())).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST, "Signal is not in the controlled alert catalog"));
        if (!signal.sourceType().equals(source)) throw new BizException(ErrorCode.BAD_REQUEST, "Signal does not match rule source");
        int window = request.windowSeconds() == null ? 300 : request.windowSeconds();
        int pending = request.pendingSeconds() == null ? 300 : request.pendingSeconds();
        if (window < 1 || window > 86400 || pending < 0 || pending > 86400) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Rule timing is outside the supported range");
        }
        Map<String, String> labels = request.labels() == null ? Map.of() : request.labels();
        if (labels.size() > 20 || labels.entrySet().stream().anyMatch(e -> e.getKey().length() > 64 || e.getValue().length() > 256)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Rule labels exceed the supported limit");
        }
        return new AlertingModels.RuleRequest(clean(request.name()), source, request.signalKey(), comparator, request.threshold(),
                window, pending, severity, request.contactGroupId(), request.enabled(), labels, request.version());
    }

    private AlertingModels.ChannelView toChannelView(AlertingRepository.ChannelRecord record) {
        Map<String, Object> decrypted = crypto.decrypt(record.encryptedConfig());
        return new AlertingModels.ChannelView(
                record.id(), record.name(), record.type(), record.enabled(), crypto.masked(decrypted), crypto.hasSecret(decrypted),
                record.lastTestStatus(), record.lastTestError(), record.lastTestAt(), record.version(), record.updatedAt()
        );
    }

    private Map<String, Object> mergeExistingSecrets(Long id, String requestedType, Map<String, Object> requested) {
        Map<String, Object> merged = new LinkedHashMap<>(requested == null ? Map.of() : requested);
        if (id == null) return merged;
        AlertingRepository.ChannelRecord existing = repository.findChannel(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Alert channel not found"));
        if (!existing.type().equals(requestedType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Alert channel type cannot be changed after creation");
        }
        Map<String, Object> prior = crypto.decrypt(existing.encryptedConfig());
        return crypto.retainExistingSecrets(prior, merged);
    }

    private static void validateMemberCompatibility(String channelType, String memberType, String target) {
        if (channelType.startsWith("EMAIL") && !"EMAIL".equals(memberType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Email channels require EMAIL contact members");
        }
        if (channelType.endsWith("WEBHOOK") && !"WEBHOOK".equals(memberType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook channels require WEBHOOK contact members");
        }
        if (Set.of("WECOM_APP", "FEISHU_APP", "DINGTALK_APP").contains(channelType)
                && !Set.of("USER", "EXTERNAL_USER", "CHAT").contains(memberType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Enterprise app channels require USER, EXTERNAL_USER or CHAT members");
        }
        if ("WECOM_APP".equals(channelType) && "CHAT".equals(memberType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "WeCom app channels require USER or EXTERNAL_USER members");
        }
        if ("USER".equals(memberType) && !target.startsWith("user:")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Lumira users must use targetIdentifier=user:<userUuid>");
        }
    }

    private static AlertingModels.CatalogSignal signal(String key, String name, String source, String unit, String description) {
        return new AlertingModels.CatalogSignal(key, name, source, unit, COMPARATORS.stream().sorted().toList(), description);
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) throw new BizException(ErrorCode.BAD_REQUEST, "Required value is missing");
        return value.trim();
    }

    private static String cleanNullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static Match match(
            AlertingRepository.LocalDirectoryUser local,
            Map<String, List<AlertDeliveryGateway.ExternalDirectoryUser>> byEmail,
            Map<String, List<AlertDeliveryGateway.ExternalDirectoryUser>> byPhone
    ) {
        String email = normalizeEmail(local.email());
        if (email != null) {
            List<AlertDeliveryGateway.ExternalDirectoryUser> candidates = byEmail.getOrDefault(email, List.of());
            if (candidates.size() == 1) return new Match(candidates.getFirst(), "EMAIL", "MATCHED");
            if (candidates.size() > 1) return new Match(null, "EMAIL", "AMBIGUOUS");
        }
        String phone = normalizePhone(local.mobile());
        if (phone != null) {
            List<AlertDeliveryGateway.ExternalDirectoryUser> candidates = byPhone.getOrDefault(phone, List.of());
            if (candidates.size() == 1) return new Match(candidates.getFirst(), "PHONE", "MATCHED");
            if (candidates.size() > 1) return new Match(null, "PHONE", "AMBIGUOUS");
        }
        return new Match(null, "NONE", "UNMATCHED");
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) return null;
        String email = value.trim().toLowerCase(Locale.ROOT);
        return email.contains("@") ? email : null;
    }

    private static String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.startsWith("86") && digits.length() == 13) digits = digits.substring(2);
        return digits.length() >= 7 ? digits : null;
    }

    private record Match(AlertDeliveryGateway.ExternalDirectoryUser user, String source, String status) { }
}
