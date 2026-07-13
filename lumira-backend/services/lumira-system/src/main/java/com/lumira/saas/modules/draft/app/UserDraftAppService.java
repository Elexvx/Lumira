package com.lumira.saas.modules.draft.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.draft.repository.UserDraftRepository;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class UserDraftAppService {
    private static final Pattern DRAFT_KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,127}");
    private static final int MAX_PAYLOAD_LENGTH = 1_000_000;

    private final UserDraftRepository repository;
    private final ObjectMapper objectMapper;

    public UserDraftAppService(UserDraftRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<Draft> find(CurrentUser user, String draftKey) {
        Owner owner = requireOwner(user);
        String key = requireKey(draftKey);
        return repository.find(owner.userId(), owner.userUuid(), key)
                .map(value -> new Draft(readJson(value.payloadJson()),
                        value.updatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
    }

    public Draft save(CurrentUser user, String draftKey, Object payload) {
        Owner owner = requireOwner(user);
        String key = requireKey(draftKey);
        if (payload == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Draft payload is required");
        }
        String json = writeJson(payload);
        if (json.length() > MAX_PAYLOAD_LENGTH) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Draft payload is too large");
        }
        repository.save(owner.userId(), owner.userUuid(), key, json);
        return find(user, key).orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR, "Draft save failed"));
    }

    public void delete(CurrentUser user, String draftKey) {
        Owner owner = requireOwner(user);
        repository.delete(owner.userId(), owner.userUuid(), requireKey(draftKey));
    }

    private Owner requireOwner(CurrentUser user) {
        if (user == null || !user.isAuthenticated() || user.getUserId() == null || user.getUserUuid() == null || user.getUserUuid().isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return new Owner(user.getUserId(), user.getUserUuid());
    }

    private String requireKey(String value) {
        String key = value == null ? "" : value.trim().toLowerCase();
        if (!DRAFT_KEY_PATTERN.matcher(key).matches()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Invalid draft key");
        }
        return key;
    }

    private Object readJson(String value) {
        try { return objectMapper.readValue(value, Object.class); }
        catch (Exception exception) { throw new BizException(ErrorCode.SYSTEM_ERROR, "Invalid stored draft"); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new BizException(ErrorCode.VALIDATION_ERROR, "Invalid draft payload"); }
    }

    public record Draft(Object payload, long updatedAt) {}
    private record Owner(Long userId, String userUuid) {}
}
