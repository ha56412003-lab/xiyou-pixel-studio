package com.cn.common.configuration;

import cn.dev33.satoken.stp.StpUtil;
import com.cn.common.constant.UserConstant;
import com.cn.common.enums.RoleEnum;
import com.cn.common.structure.UserInfoStructure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端接口鉴权：/system/** 只允许管理员调用。
 * <p>
 * 之前只有前端路由守卫（router/guards.ts）拦着，后端接口本身谁都能调，
 * 任何人拿到 token 就能改工作流、删用户。这里补上服务端校验。
 */
@Slf4j
@Configuration
public class AdminAuthConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(new AdminInterceptor())
                .addPathPatterns("/system/**");
    }

    static class AdminInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
                                 final Object handler) throws Exception {
            // 预检请求放行
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return true;
            }
            try {
                if (!StpUtil.isLogin()) {
                    writeError(response, 401, "请先登录");
                    return false;
                }
                Object info = StpUtil.getSession().get(UserConstant.USER_INFO);
                String role = info instanceof UserInfoStructure ? ((UserInfoStructure) info).getRole() : null;
                if (role == null || !RoleEnum.ADMIN.getDesc().equalsIgnoreCase(role)) {
                    writeError(response, 403, "仅管理员可操作");
                    return false;
                }
                return true;
            } catch (Exception e) {
                log.warn("管理端鉴权异常: {}", e.getMessage());
                writeError(response, 401, "登录状态已失效");
                return false;
            }
        }

        private void writeError(final HttpServletResponse response, final int code, final String msg) throws Exception {
            response.setStatus(200); // 保持 HTTP 200，业务码放 body，前端拦截器统一处理
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}");
        }
    }
}
