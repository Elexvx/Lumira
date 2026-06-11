package com.lumira.saas.modules.user.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import com.lumira.saas.modules.user.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class UserDomainService {

    private final SysUserMapper sysUserMapper;
    private final IamUserService iamUserService;

    @Autowired
    public UserDomainService(SysUserMapper sysUserMapper, IamUserService iamUserService) {
        this.sysUserMapper = sysUserMapper;
        this.iamUserService = iamUserService;
    }

    public UserDomainService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
        this.iamUserService = null;
    }

    public Optional<SysUserEntity> findLoginUser(String account) {
        if (!StringUtils.hasText(account)) {
            return Optional.empty();
        }
        if (iamUserService != null) {
            return iamUserService.findByLoginAccount(account);
        }

        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeleted, 0)
                .and(query -> query.eq(SysUserEntity::getUsername, account)
                        .or()
                        .eq(SysUserEntity::getMobile, account)
                        .or()
                        .eq(SysUserEntity::getEmail, account.trim().toLowerCase(java.util.Locale.ROOT)))
                .last("limit 1");

        return Optional.ofNullable(sysUserMapper.selectOne(wrapper));
    }

    public Optional<SysUserEntity> findById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1");
        return Optional.ofNullable(sysUserMapper.selectOne(wrapper));
    }
}
