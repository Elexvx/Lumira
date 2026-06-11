package com.lumira.saas.modules.system.passkey;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PasskeyCredentialMapper extends BaseMapper<PasskeyCredentialEntity> {
    PasskeyCredentialEntity findByCredentialId(@Param("credentialId") String credentialId);

    List<PasskeyCredentialEntity> listByUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
