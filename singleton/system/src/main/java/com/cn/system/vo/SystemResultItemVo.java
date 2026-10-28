package com.cn.system.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 系统作品管理分页项
 */
@Data
@Accessors(chain = true)
public class SystemResultItemVo {

    private Long id;

    private String taskId;

    private Long workflowId;

    private String workflowName;

    private Long userId;

    private String userAccount;

    private String type;

    private String url;

    private LocalDateTime createTime;
}
