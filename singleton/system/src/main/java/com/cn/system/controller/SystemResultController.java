package com.cn.system.controller;

import com.cn.common.enums.RoleEnum;
import com.cn.common.exceptions.UniversalException;
import com.cn.common.msg.Result;
import com.cn.common.structure.UserInfoStructure;
import com.cn.common.utils.UserUtils;
import com.cn.common.vo.PageVo;
import com.cn.system.service.SystemResultService;
import com.cn.system.vo.SystemResultItemVo;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统作品管理：全站作品查看 / 清理
 */
@RestController
@RequestMapping("/system/result")
@Validated
@RequiredArgsConstructor
public class SystemResultController {

    private final SystemResultService systemResultService;

    /** 仅管理员可访问 */
    private void requireAdmin() {
        UserInfoStructure info = UserUtils.getCurrentUserInfo();
        if (info == null || !RoleEnum.ADMIN.getDesc().equalsIgnoreCase(info.getRole())) {
            throw new UniversalException("无权限访问", 403);
        }
    }

    /**
     * 全站作品分页
     */
    @GetMapping(value = "/page", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result page(@RequestParam(defaultValue = "1") @Min(1) Integer page,
                       @RequestParam(defaultValue = "20") @Min(1) Integer size,
                       @RequestParam(required = false) Long userId,
                       @RequestParam(required = false) Long workflowId,
                       @RequestParam(required = false) String keyword) {
        requireAdmin();
        PageVo<SystemResultItemVo> data = systemResultService.page(page, size, userId, workflowId, keyword);
        return Result.data(data);
    }

    /**
     * 批量删除作品（记录 + 磁盘文件）
     */
    @PostMapping(value = "/batch-delete", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result batchDelete(@RequestBody Map<String, List<Long>> body) {
        requireAdmin();
        List<Long> ids = body == null ? null : body.get("ids");
        int count = systemResultService.batchDelete(ids);
        return Result.ok("已删除 " + count + " 条作品");
    }
}
