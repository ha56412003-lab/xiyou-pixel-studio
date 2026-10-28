package com.cn.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新工作流DTO
 *
 * @author 时间海 @github dulaiduwang003
 * @email 2074055628@qq.com
 * @version 1.0
 */
@Data
public class UpdateWorkflowDto {

    @NotNull(message = "workflowId 不能为空")
    private Long workflowId;

    @NotBlank(message = "name 不能为空")
    private String name;

    /**
     * 类别（可选；为空时不修改类别）
     */
    private String workflowCategoryId;

    /**
     * 橱窗图（作品展示）URL JSON 数组字符串，如 ["url1","url2"]
     */
    private String showcaseJson;

    /**
     * 封面图 URL（可选）
     */
    private String url;

    /**
     * 描述（可选）
     */
    private String description;

    /**
     * 模块内排序，数字越小越靠前（可选，默认 0）
     */
    private Integer sortOrder;
}


