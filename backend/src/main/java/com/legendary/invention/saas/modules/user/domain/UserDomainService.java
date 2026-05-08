package com.legendary.invention.saas.modules.user.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import com.legendary.invention.saas.modules.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Service
public class UserDomainService {

    private final SysUserMapper sysUserMapper;

    public UserDomainService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    public Optional<SysUserEntity> findLoginUser(String account) {
        if (!StringUtils.hasText(account)) {
            return Optional.empty();
        }

        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeleted, 0)
                .and(query -> query.eq(SysUserEntity::getUsername, account)
                        .or()
                        .eq(SysUserEntity::getMobile, account)
                        .or()
                        .apply("lower(email) = {0}", account.trim().toLowerCase(Locale.ROOT)))
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
