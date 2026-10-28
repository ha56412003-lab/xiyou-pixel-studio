package com.cn.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName(value = "workflow")
public class Workflow {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;

  private String description;

  private String url;

  private String json;

  private String workflowCategoryId;

  private Long creditsDeducted;

  /** 作品展示柜：JSON 数组，存 3-4 张公开展示图的 URL（对所有用户可见） */
  private String showcaseJson;

  /** 模块内排序，数字越小越靠前 */
  private Integer sortOrder;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

}
