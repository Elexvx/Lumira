package com.lumira.saas.modules.system.passkey;

import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyCredentialAppServiceTest {

    private final PasskeyCredentialMapper mapper = mock(PasskeyCredentialMapper.class);
    private final UserDomainService userDomainService = mock(UserDomainService.class);
    private final PasskeyCredentialAppService service = new PasskeyCredentialAppService(mapper, userDomainService);

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PasskeyCredentialEntity.class
        );
    }

    @Test
    void createRejectsInvalidOwnerBeforeInsert() {
        PasskeyCredentialSaveRequestDTO request = new PasskeyCredentialSaveRequestDTO(
                0L,
                "user-uuid-0",
                "handle-1",
                "credential-1",
                "public-key",
                1L,
                null,
                false,
                false,
                "Laptop"
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class);

        verify(mapper, never()).insert(any(PasskeyCredentialEntity.class));
    }

    @Test
    void createRejectsBlankCredentialMaterialBeforeInsert() {
        when(userDomainService.findById(42L)).thenReturn(Optional.of(user(42L)));
        PasskeyCredentialSaveRequestDTO request = new PasskeyCredentialSaveRequestDTO(
                42L,
                "user-uuid-42",
                "handle-1",
                " ",
                "public-key",
                1L,
                null,
                false,
                false,
                "Laptop"
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class);

        verify(mapper, never()).insert(any(PasskeyCredentialEntity.class));
    }

    @Test
    void createRejectsMissingOwnerBeforeInsert() {
        when(userDomainService.findById(42L)).thenReturn(Optional.empty());
        PasskeyCredentialSaveRequestDTO request = new PasskeyCredentialSaveRequestDTO(
                42L,
                "user-uuid-42",
                "handle-1",
                "credential-1",
                "public-key",
                1L,
                null,
                false,
                false,
                "Laptop"
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class);

        verify(mapper, never()).insert(any(PasskeyCredentialEntity.class));
    }

    @Test
    void createRejectsDisabledOwnerBeforeInsert() {
        SysUserEntity disabledUser = user(42L);
        disabledUser.setStatus("DISABLED");
        when(userDomainService.findById(42L)).thenReturn(Optional.of(disabledUser));
        PasskeyCredentialSaveRequestDTO request = new PasskeyCredentialSaveRequestDTO(
                42L,
                "user-uuid-42",
                "handle-1",
                "credential-1",
                "public-key",
                1L,
                null,
                false,
                false,
                "Laptop"
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class);

        verify(mapper, never()).insert(any(PasskeyCredentialEntity.class));
    }

    @Test
    void createWritesCredentialWithTrustedOwner() {
        when(userDomainService.findById(42L)).thenReturn(Optional.of(user(42L)));
        PasskeyCredentialEntity stored = new PasskeyCredentialEntity();
        stored.setId(100L);
        stored.setUserId(42L);
        stored.setUserUuid("user-uuid-42");
        stored.setCredentialId("credential-1");
        stored.setPublicKeyCose("public-key");
        when(mapper.findByCredentialId("credential-1")).thenReturn(stored);
        when(mapper.insert(any(PasskeyCredentialEntity.class))).thenReturn(1);

        PasskeyCredentialSaveRequestDTO request = new PasskeyCredentialSaveRequestDTO(
                42L,
                "user-uuid-42",
                " handle-1 ",
                " credential-1 ",
                " public-key ",
                null,
                "usb",
                true,
                false,
                " Laptop "
        );

        var credential = service.create(request);

        ArgumentCaptor<PasskeyCredentialEntity> entityCaptor = ArgumentCaptor.forClass(PasskeyCredentialEntity.class);
        verify(mapper).insert(entityCaptor.capture());
        PasskeyCredentialEntity inserted = entityCaptor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(42L);
        assertThat(inserted.getUserUuid()).isEqualTo("user-uuid-42");
        assertThat(inserted.getCreatedBy()).isEqualTo(42L);
        assertThat(inserted.getCreatedByUuid()).isEqualTo("user-uuid-42");
        assertThat(inserted.getUpdatedBy()).isEqualTo(42L);
        assertThat(inserted.getUpdatedByUuid()).isEqualTo("user-uuid-42");
        assertThat(inserted.getCredentialId()).isEqualTo("credential-1");
        assertThat(inserted.getPublicKeyCose()).isEqualTo("public-key");
        assertThat(inserted.getSignCount()).isZero();
        assertThat(credential.id()).isEqualTo(100L);
    }

    @Test
    void createRejectsWhenInsertMisses() {
        when(userDomainService.findById(42L)).thenReturn(Optional.of(user(42L)));
        when(mapper.insert(any(PasskeyCredentialEntity.class))).thenReturn(0);
        PasskeyCredentialSaveRequestDTO request = new PasskeyCredentialSaveRequestDTO(
                42L,
                "user-uuid-42",
                "handle-1",
                "credential-1",
                "public-key",
                1L,
                null,
                false,
                false,
                "Laptop"
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void renameWritesUpdatedByUuidAndKeepsOwnerUuidPredicate() {
        PasskeyCredentialEntity stored = new PasskeyCredentialEntity();
        stored.setId(100L);
        stored.setUserId(42L);
        stored.setUserUuid("user-uuid-42");
        when(mapper.listByUser(42L, "user-uuid-42")).thenReturn(java.util.List.of(stored));
        when(mapper.update(any(), any())).thenReturn(1);

        service.rename(100L, 42L, "user-uuid-42", "Laptop");

        ArgumentCaptor<Wrapper<PasskeyCredentialEntity>> wrapperCaptor = wrapperCaptor();
        verify(mapper).update(any(), wrapperCaptor.capture());
        String sqlSet = ((LambdaUpdateWrapper<PasskeyCredentialEntity>) wrapperCaptor.getValue()).getSqlSet();
        assertThat(sqlSet).contains("updated_by", "updated_by_uuid");
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("user_uuid", "deleted");
    }

    @Test
    void renameRejectsWhenOwnerScopedWriteMisses() {
        when(mapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rename(100L, 42L, "user-uuid-42", "Laptop"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void deleteWritesUpdatedByUuidAndKeepsOwnerUuidPredicate() {
        service.delete(100L, 42L, "user-uuid-42");

        ArgumentCaptor<Wrapper<PasskeyCredentialEntity>> wrapperCaptor = wrapperCaptor();
        verify(mapper).update(any(), wrapperCaptor.capture());
        String sqlSet = ((LambdaUpdateWrapper<PasskeyCredentialEntity>) wrapperCaptor.getValue()).getSqlSet();
        assertThat(sqlSet).contains("updated_by", "updated_by_uuid");
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("user_uuid", "deleted");
    }

    @Test
    void updateUsageRejectsInvalidCredentialIdBeforeUpdate() {
        assertThatThrownBy(() -> service.updateUsage(new PasskeyCredentialUsageRequestDTO(0L, 42L, "user-uuid-42", 1L, false, false)))
                .isInstanceOf(BizException.class);

        verify(mapper, never()).update(any(), any());
    }

    @Test
    void updateUsageKeepsOwnerUuidPredicate() {
        service.updateUsage(new PasskeyCredentialUsageRequestDTO(100L, 42L, "user-uuid-42", 1L, false, false));

        ArgumentCaptor<Wrapper<PasskeyCredentialEntity>> wrapperCaptor = wrapperCaptor();
        verify(mapper).update(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("user_id", "user_uuid", "deleted");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Wrapper<PasskeyCredentialEntity>> wrapperCaptor() {
        return ArgumentCaptor.forClass((Class) Wrapper.class);
    }

    @Test
    void updateUsageRejectsNegativeSignCountBeforeUpdate() {
        assertThatThrownBy(() -> service.updateUsage(new PasskeyCredentialUsageRequestDTO(100L, 42L, "user-uuid-42", -1L, false, false)))
                .isInstanceOf(BizException.class);

        verify(mapper, never()).update(any(), any());
    }

    private static SysUserEntity user(Long id) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setUuid("user-uuid-" + id);
        user.setUsername("alice");
        user.setStatus("ENABLED");
        user.setDeleted(0);
        return user;
    }
}
