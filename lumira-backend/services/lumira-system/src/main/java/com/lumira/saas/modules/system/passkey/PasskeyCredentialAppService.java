package com.lumira.saas.modules.system.passkey;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PasskeyCredentialAppService {
    private final PasskeyCredentialMapper passkeyCredentialMapper;

    public PasskeyCredentialAppService(PasskeyCredentialMapper passkeyCredentialMapper) {
        this.passkeyCredentialMapper = passkeyCredentialMapper;
    }

    public PasskeyCredentialDTO findByCredentialId(String credentialId) {
        if (!StringUtils.hasText(credentialId)) {
            return null;
        }
        return map(passkeyCredentialMapper.findByCredentialId(credentialId));
    }

    public List<PasskeyCredentialDTO> list(Long tenantId, Long userId) {
        return passkeyCredentialMapper.listByUser(tenantId, userId).stream().map(this::map).toList();
    }

    public PasskeyCredentialDTO create(PasskeyCredentialSaveRequestDTO request) {
        try {
            PasskeyCredentialEntity entity = new PasskeyCredentialEntity();
            entity.setTenantId(request.tenantId());
            entity.setUserId(request.userId());
            entity.setUserHandle(request.userHandle());
            entity.setCredentialId(request.credentialId());
            entity.setPublicKeyCose(request.publicKeyCose());
            entity.setSignCount(request.signCount() == null ? 0L : request.signCount());
            entity.setTransports(request.transports());
            entity.setBackupEligible(Boolean.TRUE.equals(request.backupEligible()));
            entity.setBackupState(Boolean.TRUE.equals(request.backupState()));
            entity.setLabel(StringUtils.hasText(request.label()) ? request.label().trim() : "通行密钥");
            entity.setDeleted(0);
            entity.setCreatedBy(request.userId());
            entity.setUpdatedBy(request.userId());
            passkeyCredentialMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("该通行密钥已绑定");
        }
        return findByCredentialId(request.credentialId());
    }

    public PasskeyCredentialDTO rename(Long id, Long tenantId, Long userId, String label) {
        passkeyCredentialMapper.update(null, new LambdaUpdateWrapper<PasskeyCredentialEntity>()
                .set(PasskeyCredentialEntity::getLabel, StringUtils.hasText(label) ? label.trim() : "通行密钥")
                .set(PasskeyCredentialEntity::getUpdatedBy, userId)
                .set(PasskeyCredentialEntity::getUpdatedAt, LocalDateTime.now())
                .eq(PasskeyCredentialEntity::getId, id)
                .eq(PasskeyCredentialEntity::getTenantId, tenantId)
                .eq(PasskeyCredentialEntity::getUserId, userId)
                .eq(PasskeyCredentialEntity::getDeleted, 0));
        return list(tenantId, userId).stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
    }

    public boolean delete(Long id, Long tenantId, Long userId) {
        return passkeyCredentialMapper.update(null, new LambdaUpdateWrapper<PasskeyCredentialEntity>()
                .set(PasskeyCredentialEntity::getDeleted, 1)
                .set(PasskeyCredentialEntity::getUpdatedBy, userId)
                .set(PasskeyCredentialEntity::getUpdatedAt, LocalDateTime.now())
                .eq(PasskeyCredentialEntity::getId, id)
                .eq(PasskeyCredentialEntity::getTenantId, tenantId)
                .eq(PasskeyCredentialEntity::getUserId, userId)
                .eq(PasskeyCredentialEntity::getDeleted, 0)) > 0;
    }

    public boolean updateUsage(PasskeyCredentialUsageRequestDTO request) {
        LocalDateTime now = LocalDateTime.now();
        return passkeyCredentialMapper.update(null, new LambdaUpdateWrapper<PasskeyCredentialEntity>()
                .setSql("sign_count = greatest(sign_count, {0})", request.signCount() == null ? 0L : request.signCount())
                .set(PasskeyCredentialEntity::getBackupEligible, Boolean.TRUE.equals(request.backupEligible()))
                .set(PasskeyCredentialEntity::getBackupState, Boolean.TRUE.equals(request.backupState()))
                .set(PasskeyCredentialEntity::getLastUsedAt, now)
                .set(PasskeyCredentialEntity::getUpdatedAt, now)
                .eq(PasskeyCredentialEntity::getId, request.credentialId())
                .eq(PasskeyCredentialEntity::getDeleted, 0)) > 0;
    }

    private PasskeyCredentialDTO map(PasskeyCredentialEntity entity) {
        return entity == null ? null : new PasskeyCredentialDTO(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getUserHandle(),
                entity.getCredentialId(),
                entity.getPublicKeyCose(),
                entity.getSignCount(),
                entity.getTransports(),
                entity.getBackupEligible(),
                entity.getBackupState(),
                entity.getLabel(),
                entity.getCreatedAt(),
                entity.getLastUsedAt()
        );
    }
}
