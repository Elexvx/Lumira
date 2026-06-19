package com.lumira.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.message.app.PlatformEventOutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessagePlatformEventOutboxMapper extends BaseMapper<PlatformEventOutboxEntity> {

    @Select("""
            select id, tenant_id as tenantId, user_id as userId, source_type as sourceType,
                   event_type as eventType, event_key as eventKey, payload_json as payloadJson,
                   dispatch_status as dispatchStatus, retry_count as retryCount,
                   next_retry_at as nextRetryAt, delivered_at as deliveredAt, last_error as lastError,
                   trace_id as traceId, request_id as requestId, created_by as createdBy,
                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
            from platform_event_outbox force index (idx_platform_event_outbox_owner_queue)
            where deleted = 0
              and source_type = #{sourceType}
              and (
                    dispatch_status = #{recordedStatus}
                    or (dispatch_status = #{failedStatus} and (next_retry_at is null or next_retry_at <= #{now}))
              )
            order by created_at asc, id asc
            limit #{limit}
            """)
    List<PlatformEventOutboxEntity> listDispatchable(
            @Param("sourceType") String sourceType,
            @Param("recordedStatus") String recordedStatus,
            @Param("failedStatus") String failedStatus,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );
}
