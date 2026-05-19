package com.legendary.invention.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legendary.invention.message.dto.MessageQueryModels.DeliveryLogQuery;
import com.legendary.invention.message.entity.MessageDeliveryLogEntity;
import com.legendary.invention.message.vo.MessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageDeliveryLogMapper extends BaseMapper<MessageDeliveryLogEntity> {

    Long countDeliveryLogs(@Param("query") DeliveryLogQuery query);

    List<MessageVO.DeliveryLogVO> listDeliveryLogs(@Param("query") DeliveryLogQuery query);
}
