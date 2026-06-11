package com.lumira.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.message.dto.MessageQueryModels.NoticeArchiveQuery;
import com.lumira.message.dto.MessageQueryModels.RecipientRow;
import com.lumira.message.entity.MessageNoticeEntity;
import com.lumira.message.vo.MessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageNoticeMapper extends BaseMapper<MessageNoticeEntity> {

    Long countVisiblePublished(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    List<MessageVO.NoticeVO> listVisiblePublished(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("limit") long limit, @Param("offset") long offset);

    Long countUnread(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int markAllRead(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    Long countArchive(@Param("query") NoticeArchiveQuery query);

    List<MessageVO.NoticeVO> listArchive(@Param("query") NoticeArchiveQuery query);

    MessageVO.NoticeVO findNoticeById(@Param("tenantId") Long tenantId, @Param("noticeId") Long noticeId, @Param("userId") Long userId);

    MessageVO.NoticeVO findVisibleNoticeById(@Param("tenantId") Long tenantId, @Param("noticeId") Long noticeId, @Param("userId") Long userId);

    int upsertRead(@Param("tenantId") Long tenantId, @Param("noticeId") Long noticeId, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    List<RecipientRow> listTenantRecipients(@Param("tenantId") Long tenantId);

    List<RecipientRow> listRoleRecipients(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    List<RecipientRow> listUserRecipient(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
