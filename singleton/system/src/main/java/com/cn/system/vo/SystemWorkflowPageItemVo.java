package com.cn.system.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 系统工作流分页项VO
 *
 * @author 时间海 @github dulaiduwang003
 * @email 2074055628@qq.com
 * @version 1.0
 */
@Data
@Accessors(chain = true)
public class SystemWorkflowPageItemVo {

    private Long workflowId;

    private String name;

    private String description;

    private String url;

    private String categoryName;

    private Long creditsDeducted;

    /**
     * 橱窗图（作品展示）URL JSON 数组字符串
     */
    private String showcaseJson;

    /**
     * 模块内排序，数字越小越靠前
     */
    private Integer sortOrder;
}


