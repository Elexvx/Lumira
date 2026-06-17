package com.lumira.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.message.dto.MessageQueryModels.NoticeArchiveQuery;
import com.lumira.message.entity.MessageNoticeEntity;
import com.lumira.message.vo.MessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageNoticeMapper extends BaseMapper<MessageNoticeEntity> {

    List<MessageVO.NoticeVO> listVisiblePublished(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("roleIds") List<Long> roleIds,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    Long countUnread(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("roleIds") List<Long> roleIds,
            @Param("limit") long limit
    );

    int markAllRead(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("roleIds") List<Long> roleIds,
            @Param("readAt") LocalDateTime readAt
    );

    Long countArchive(@Param("query") NoticeArchiveQuery query);

    List<MessageVO.NoticeVO> listArchive(@Param("query") NoticeArchiveQuery query);

    MessageVO.NoticeVO findNoticeById(@Param("tenantId") Long tenantId, @Param("noticeId") Long noticeId, @Param("userId") Long userId);

    MessageVO.NoticeVO findVisibleNoticeById(
            @Param("tenantId") Long tenantId,
            @Param("noticeId") Long noticeId,
            @Param("userId") Long userId,
            @Param("roleIds") List<Long> roleIds
    );

    int upsertRead(@Param("tenantId") Long tenantId, @Param("noticeId") Long noticeId, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

}
