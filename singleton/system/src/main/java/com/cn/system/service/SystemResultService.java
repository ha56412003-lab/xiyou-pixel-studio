package com.cn.system.service;

import com.cn.common.vo.PageVo;
import com.cn.system.vo.SystemResultItemVo;

import java.util.List;

/**
 * 系统作品管理（全站作品查看 / 清理）
 */
public interface SystemResultService {

    /**
     * 全站作品分页
     *
     * @param page       页码
     * @param size       每页条数
     * @param userId     按用户筛选（可空）
     * @param workflowId 按商品筛选（可空）
     * @param keyword    按商品名 / 任务ID 搜索（可空）
     */
    PageVo<SystemResultItemVo> page(Integer page, Integer size, Long userId, Long workflowId, String keyword);

    /**
     * 批量删除作品（同时删除磁盘文件）
     *
     * @return 实际删除条数
     */
    int batchDelete(List<Long> ids);
}
