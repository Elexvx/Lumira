package com.lumira.saas.modules.system.passkey;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lumira.api.system.PasskeyCredentialAssertionDTO;
import com.lumira.api.system.PasskeyCredentialDescriptorDTO;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.user.domain.UserDomainService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PasskeyCredentialAppService {
    private static final String DEFAULT_LABEL = "Passkey";

    private final PasskeyCredentialMapper passkeyCredentialMapper;
    private final UserDomainService userDomainService;

    public PasskeyCredentialAppService(PasskeyCredentialMapper passkeyCredentialMapper, UserDomainService userDomainService) {
        this.passkeyCredentialMapper = passkeyCredentialMapper;
        this.userDomainService = userDomainService;
    }

    public PasskeyCredentialAssertionDTO findAssertionByCredentialId(String credentialId) {
        if (!StringUtils.hasText(credentialId)) {
            return null;
        }
        return mapAssertion(passkeyCredentialMapper.findByCredentialId(credentialId.trim()));
    }

    public List<PasskeyCredentialDescriptorDTO> listDescriptors(Long userId, String userUuid) {
        return passkeyCredentialMapper.listByUser(
                requirePositiveId(userId, "userId"),
                requireText(userUuid, "userUuid")
        ).stream().map(this::mapDescriptor).toList();
    }

    public List<PasskeyCredentialDTO> list(Long userId, String userUuid) {
        return passkeyCredentialMapper.listByUser(
                requirePositiveId(userId, "userId"),
                requireText(userUuid, "userUuid")
        ).stream().map(this::map).toList();
    }

    public PasskeyCredentialDTO create(PasskeyCredentialSaveRequestDTO request) {
        requireRequest(request, "passkey credential request");
        Long userId = requirePositiveId(request.userId(), "userId");
        String userUuid = requireText(request.userUuid(), "userUuid");
        requireExistingUser(userId, userUuid);
        String userHandle = requireText(request.userHandle(), "userHandle");
        String credentialId = requireText(request.credentialId(), "credentialId");
        String publicKeyCose = requireText(request.publicKeyCose(), "publicKeyCose");
        Long signCount = normalizeSignCount(request.signCount());

        try {
            PasskeyCredentialEntity entity = new PasskeyCredentialEntity();
            entity.setUserId(userId);
            entity.setUserUuid(userUuid);
            entity.setUserHandle(userHandle);
            entity.setCredentialId(credentialId);
            entity.setPublicKeyCose(publicKeyCose);
            entity.setSignCount(signCount);
            entity.setTransports(request.transports());
            entity.setBackupEligible(Boolean.TRUE.equals(request.backupEligible()));
            entity.setBackupState(Boolean.TRUE.equals(request.backupState()));
            entity.setLabel(StringUtils.hasText(request.label()) ? request.label().trim() : DEFAULT_LABEL);
            entity.setDeleted(0);
            entity.setCreatedBy(userId);
            entity.setCreatedByUuid(userUuid);
            entity.setUpdatedBy(userId);
            entity.setUpdatedByUuid(userUuid);
            int inserted = passkeyCredentialMapper.insert(entity);
            requireCredentialWrite(inserted, "Passkey credential changed, please retry");
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Passkey is already registered");
        }
        return map(passkeyCredentialMapper.findByCredentialId(credentialId));
    }

    public PasskeyCredentialDTO rename(Long id, Long userId, String userUuid, String label) {
        Long credentialRecordId = requirePositiveId(id, "id");
        Long ownerUserId = requirePositiveId(userId, "userId");
        String ownerUserUuid = requireText(userUuid, "userUuid");
        int updated = passkeyCredentialMapper.update(null, new LambdaUpdateWrapper<PasskeyCredentialEntity>()
                .set(PasskeyCredentialEntity::getLabel, StringUtils.hasText(label) ? label.trim() : DEFAULT_LABEL)
                .set(PasskeyCredentialEntity::getUpdatedBy, ownerUserId)
                .set(PasskeyCredentialEntity::getUpdatedByUuid, ownerUserUuid)
                .set(PasskeyCredentialEntity::getUpdatedAt, LocalDateTime.now())
                .eq(PasskeyCredentialEntity::getId, credentialRecordId)
                .eq(PasskeyCredentialEntity::getUserId, ownerUserId)
                .eq(PasskeyCredentialEntity::getUserUuid, ownerUserUuid)
                .eq(PasskeyCredentialEntity::getDeleted, 0));
        requireCredentialWrite(updated, "Passkey credential changed, please retry");
        return list(ownerUserId, ownerUserUuid).stream().filter(item -> credentialRecordId.equals(item.id())).findFirst().orElse(null);
    }

    public boolean delete(Long id, Long userId, String userUuid) {
        Long credentialRecordId = requirePositiveId(id, "id");
        Long ownerUserId = requirePositiveId(userId, "userId");
        String ownerUserUuid = requireText(userUuid, "userUuid");
        return passkeyCredentialMapper.update(null, new LambdaUpdateWrapper<PasskeyCredentialEntity>()
                .set(PasskeyCredentialEntity::getDeleted, 1)
                .set(PasskeyCredentialEntity::getUpdatedBy, ownerUserId)
                .set(PasskeyCredentialEntity::getUpdatedByUuid, ownerUserUuid)
                .set(PasskeyCredentialEntity::getUpdatedAt, LocalDateTime.now())
                .eq(PasskeyCredentialEntity::getId, credentialRecordId)
                .eq(PasskeyCredentialEntity::getUserId, ownerUserId)
                .eq(PasskeyCredentialEntity::getUserUuid, ownerUserUuid)
                .eq(PasskeyCredentialEntity::getDeleted, 0)) > 0;
    }

    public boolean updateUsage(PasskeyCredentialUsageRequestDTO request) {
        requireRequest(request, "passkey credential usage request");
        Long credentialRecordId = requirePositiveId(request.credentialId(), "credentialId");
        Long ownerUserId = requirePositiveId(request.userId(), "userId");
        String ownerUserUuid = requireText(request.userUuid(), "userUuid");
        Long signCount = normalizeSignCount(request.signCount());
        LocalDateTime now = LocalDateTime.now();
        return passkeyCredentialMapper.update(null, new LambdaUpdateWrapper<PasskeyCredentialEntity>()
                .setSql("sign_count = greatest(sign_count, {0})", signCount)
                .set(PasskeyCredentialEntity::getBackupEligible, Boolean.TRUE.equals(request.backupEligible()))
                .set(PasskeyCredentialEntity::getBackupState, Boolean.TRUE.equals(request.backupState()))
                .set(PasskeyCredentialEntity::getLastUsedAt, now)
                .set(PasskeyCredentialEntity::getUpdatedAt, now)
                .eq(PasskeyCredentialEntity::getId, credentialRecordId)
                .eq(PasskeyCredentialEntity::getUserId, ownerUserId)
                .eq(PasskeyCredentialEntity::getUserUuid, ownerUserUuid)
                .eq(PasskeyCredentialEntity::getDeleted, 0)) > 0;
    }

    private static void requireRequest(Object request, String fieldName) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " is required");
        }
    }

    private static Long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " must be positive");
        }
        return id;
    }

    private static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, fieldName + " is required");
        }
        return value.trim();
    }

    private static Long normalizeSignCount(Long signCount) {
        if (signCount == null) {
            return 0L;
        }
        if (signCount < 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "signCount must not be negative");
        }
        return signCount;
    }

    private void requireExistingUser(Long userId, String userUuid) {
        if (userDomainService == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "Passkey owner user does not exist");
        }
        var user = userDomainService.findById(userId)
                .filter(candidate -> userUuid.equals(candidate.getUuid()))
                .orElse(null);
        if (user == null || !"ENABLED".equalsIgnoreCase(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "Passkey owner user does not exist");
        }
    }

    private static void requireCredentialWrite(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, message);
        }
    }

    private PasskeyCredentialDTO map(PasskeyCredentialEntity entity) {
        return entity == null ? null : new PasskeyCredentialDTO(
                entity.getId(),
                entity.getLabel(),
                entity.getCreatedAt(),
                entity.getLastUsedAt()
        );
    }

    private PasskeyCredentialAssertionDTO mapAssertion(PasskeyCredentialEntity entity) {
        return entity == null ? null : new PasskeyCredentialAssertionDTO(
                entity.getId(),
                entity.getUserId(),
                entity.getUserUuid(),
                entity.getUserHandle(),
                entity.getCredentialId(),
                entity.getPublicKeyCose(),
                entity.getSignCount()
        );
    }

    private PasskeyCredentialDescriptorDTO mapDescriptor(PasskeyCredentialEntity entity) {
        return entity == null ? null : new PasskeyCredentialDescriptorDTO(
                entity.getCredentialId(),
                entity.getTransports()
        );
    }
}
