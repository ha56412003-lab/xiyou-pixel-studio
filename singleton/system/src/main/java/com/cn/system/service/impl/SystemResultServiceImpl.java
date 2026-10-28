package com.cn.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cn.common.entity.User;
import com.cn.common.entity.WorkflowResult;
import com.cn.common.mapper.UserMapper;
import com.cn.common.mapper.WorkflowResultMapper;
import com.cn.common.utils.UploadUtil;
import com.cn.common.vo.PageVo;
import com.cn.system.service.SystemResultService;
import com.cn.system.vo.SystemResultItemVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统作品管理实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemResultServiceImpl implements SystemResultService {

    private final WorkflowResultMapper resultMapper;
    private final UserMapper userMapper;
    private final UploadUtil uploadUtil;

    @Override
    public PageVo<SystemResultItemVo> page(final Integer page, final Integer size,
                                           final Long userId, final Long workflowId, final String keyword) {
        LambdaQueryWrapper<WorkflowResult> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(WorkflowResult::getUserId, userId);
        }
        if (workflowId != null) {
            wrapper.eq(WorkflowResult::getWorkflowId, workflowId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(WorkflowResult::getWorkflowName, keyword)
                    .or().like(WorkflowResult::getTaskId, keyword));
        }
        wrapper.orderByDesc(WorkflowResult::getId)
                // 列表不需要 form_params（每条几十 KB）
                .select(WorkflowResult::getId, WorkflowResult::getTaskId, WorkflowResult::getWorkflowId,
                        WorkflowResult::getWorkflowName, WorkflowResult::getUserId,
                        WorkflowResult::getType, WorkflowResult::getUrl, WorkflowResult::getCreateTime);

        Page<WorkflowResult> result = resultMapper.selectPage(new Page<>(page, size), wrapper);

        // 补齐用户账号（避免前端再查一遍）
        List<Long> userIds = result.getRecords().stream()
                .map(WorkflowResult::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> accountMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectList(
                    new LambdaQueryWrapper<User>().in(User::getId, userIds));
            for (User u : users) {
                accountMap.put(u.getId(), u.getAccount() != null ? u.getAccount() : u.getEmail());
            }
        }

        List<SystemResultItemVo> items = new ArrayList<>();
        for (WorkflowResult r : result.getRecords()) {
            items.add(new SystemResultItemVo()
                    .setId(r.getId())
                    .setTaskId(r.getTaskId())
                    .setWorkflowId(r.getWorkflowId())
                    .setWorkflowName(r.getWorkflowName())
                    .setUserId(r.getUserId())
                    .setUserAccount(accountMap.getOrDefault(r.getUserId(), "-"))
                    .setType(r.getType())
                    .setUrl(r.getUrl())
                    .setCreateTime(r.getCreateTime()));
        }

        return new PageVo<SystemResultItemVo>().setTotal(result.getTotal()).setItems(items);
    }

    @Override
    public int batchDelete(final List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<WorkflowResult> records = resultMapper.selectList(
                new LambdaQueryWrapper<WorkflowResult>().in(WorkflowResult::getId, ids));
        if (records.isEmpty()) {
            return 0;
        }
        int deleted = resultMapper.delete(
                new LambdaQueryWrapper<WorkflowResult>().in(WorkflowResult::getId, ids));

        // 同步清理磁盘文件
        int fileCount = 0;
        for (WorkflowResult r : records) {
            try {
                if (uploadUtil.deleteByUrl(r.getUrl())) {
                    fileCount++;
                }
            } catch (Exception e) {
                log.warn("删除作品文件失败(忽略): {}", r.getUrl());
            }
        }
        log.info("后台批量删除作品 {} 条，清理文件 {} 个", deleted, fileCount);
        return deleted;
    }
}
