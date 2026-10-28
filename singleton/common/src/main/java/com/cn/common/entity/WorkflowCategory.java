package com.cn.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Comfyui工作流筛选实体类
 */

@Data
@Accessors(chain = true)
@TableName(value = "workflow_category")
public class WorkflowCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 首页模块展示排序，数字越小越靠前 */
    private Integer sortOrder;


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
