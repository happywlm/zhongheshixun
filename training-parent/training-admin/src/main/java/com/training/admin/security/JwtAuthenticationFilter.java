package com.training.admin.security;

import com.training.common.constants.CommonConstants;
import com.training.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 认证过滤器（替代旧 JwtInterceptor）
 * <p>解析 Authorization Header → 加载 UserDetails → 写入 SecurityContext。
 * 失败时不直接写 response，由 ExceptionTranslationFilter 走 EntryPoint 返回 401。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * 白名单路径（直接放行）
     */
    private static final List<String> WHITELIST = Arrays.asList(
            "/admin/login",
            "/api/wx/login",
            "/error"
    );

    private final JwtUtils jwtUtils;
    private final RbacUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, RbacUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 每个请求创建新的空 SecurityContext，避免跨请求缓存
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());

        try {
            String uri = request.getRequestURI();

            // 1. 白名单放行
            if (isWhitelisted(uri)) {
                chain.doFilter(request, response);
                return;
            }

            // 2. 取 Authorization Header
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith(CommonConstants.BEARER_PREFIX)) {
                chain.doFilter(request, response);
                return;
            }

            String token = header.substring(CommonConstants.BEARER_PREFIX.length());
            if (token.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }

            // 3. 校验 token
            if (!jwtUtils.validate(token)) {
                log.debug("[RBAC] Token 无效：{}", uri);
                chain.doFilter(request, response);
                return;
            }

            // 4. 解析 token 取 username
            Claims claims = jwtUtils.parse(token);
            String username = claims.get("username", String.class);
            if (username == null || username.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }

            // 5. 加载 UserDetails
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // 6. 构建 Authentication 写入 SecurityContext
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);
        } catch (Exception e) {
            // 异常必须打堆栈 + 中断链路，避免被吞成 500 无任何线索
            // [安全修复 / #11 评审项] 不把 e.getMessage() 直接回写前端，避免泄露内部细节（如 "JWT signature does not match"）
            // 仅 server 端 log.warn 记录原始异常，前端统一返回中文 "认证失败，请重新登录"
            log.warn("[RBAC] 认证流程异常，URI={}, msg={}", request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":401,\"message\":\"认证失败，请重新登录\"}");
            } catch (IOException ioEx) {
                log.error("[RBAC] 写入 401 响应失败", ioEx);
            }
            return;
        }
    }

    /**
     * 判断是否白名单路径
     */
    private boolean isWhitelisted(String uri) {
        // 精确匹配 + swagger 前缀匹配
        if (WHITELIST.contains(uri)) {
            return true;
        }
        if (uri.startsWith("/swagger-ui/") || uri.startsWith("/v3/api-docs/")) {
            return true;
        }
        return false;
    }
}
