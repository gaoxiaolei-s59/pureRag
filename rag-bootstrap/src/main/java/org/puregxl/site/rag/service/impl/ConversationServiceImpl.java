package org.puregxl.site.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.rag.dao.entity.ConversationDO;
import org.puregxl.site.rag.dao.mapper.ConversationMapper;
import org.puregxl.site.rag.dto.resp.ConversationResponse;
import org.puregxl.site.rag.service.ConversationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;

    /**
     * 查询用户的会话列表
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    @Override
    public List<ConversationResponse> queryConversation(String userId) {
        LambdaQueryWrapper<ConversationDO> queryWrapper = Wrappers.lambdaQuery(ConversationDO.class)
                .eq(ConversationDO::getUserId, userId)
                .orderByDesc(ConversationDO::getPinned)
                .orderByDesc(ConversationDO::getUpdateTime);

        List<ConversationDO> conversationList = conversationMapper.selectList(queryWrapper);

        if (conversationList == null || conversationList.isEmpty()) {
            return List.of();
        }

        return conversationList.stream()
                .map(conversation -> {
                    ConversationResponse response = new ConversationResponse();
                    BeanUtils.copyProperties(conversation, response);
                    return response;
                })
                .toList();
    }
}
