package com.lumira.team;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.team.app.TeamAppService;
import com.lumira.team.app.TeamInternalApiService;
import com.lumira.team.app.TeamInviteService;
import com.lumira.team.app.TeamPermissionService;
import com.lumira.team.controller.InternalTeamController;
import com.lumira.team.controller.TeamV2Controller;
import com.lumira.team.infrastructure.persistence.JdbcTeamInviteRepository;
import com.lumira.team.infrastructure.persistence.JdbcTeamJoinRequestRepository;
import com.lumira.team.infrastructure.persistence.JdbcTeamMemberRepository;
import com.lumira.team.infrastructure.persistence.JdbcTeamRepository;
import com.lumira.team.infrastructure.persistence.MyBatisQueryOperations;
import com.lumira.team.infrastructure.persistence.RawSqlMapper;
import com.lumira.team.infrastructure.audit.SystemOwnerTeamAuditPort;
import com.lumira.team.mapper.TeamMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@MapperScan(
        basePackageClasses = {
                TeamMapper.class,
                RawSqlMapper.class
        },
        annotationClass = Mapper.class
)
@Import({
        SystemOwnerTeamAuditPort.class,
        TeamAppService.class,
        TeamInternalApiService.class,
        TeamInviteService.class,
        TeamPermissionService.class,
        InternalTeamController.class,
        TeamV2Controller.class,
        JdbcTeamInviteRepository.class,
        JdbcTeamJoinRequestRepository.class,
        JdbcTeamMemberRepository.class,
        JdbcTeamRepository.class,
        MyBatisQueryOperations.class
})
public class TeamControlPlaneAssemblyConfiguration {
}
