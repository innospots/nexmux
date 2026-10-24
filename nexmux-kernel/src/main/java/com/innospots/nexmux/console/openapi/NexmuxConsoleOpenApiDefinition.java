package com.innospots.nexmux.console.openapi;

import jakarta.ws.rs.Path;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.innospots.nexus.core.openapi.NexusOpenApiSecurityNames;

/**
 * Nexmux 控制台 OpenAPI 全局元数据（构建期扫描；非 JAX-RS {@code Application}，以便与 Nexus 模块共宿主）。
 *
 * <p>带 {@link Path} 以便 SmallRye JAX-RS 扫描器拾取 {@link OpenAPIDefinition}；
 * 类本身无资源方法，运行时不额外暴露 HTTP 端点。产物写入
 * {@code META-INF/nexus-openapi/nexmux-kernel.yaml}。</p>
 */
@Path("/api/d")
@OpenAPIDefinition(
        info = @Info(
                title = "Nexmux Console API",
                version = "1.0.0",
                description = "Nexmux 模型网关管理控制台 Jakarta REST 契约；运行时仅暴露构建期生成的 OpenAPI。",
                contact = @Contact(name = "Innospots Nexmux")
        ),
        tags = {
                @Tag(name = "NexmuxProvider", description = "模型服务提供方"),
                @Tag(name = "NexmuxModel", description = "模型与调用配置"),
                @Tag(name = "NexmuxRoute", description = "路由与发布"),
                @Tag(name = "NexmuxClient", description = "接入客户端与授权"),
                @Tag(name = "NexmuxOps", description = "请求与审计日志"),
                @Tag(name = "NexmuxDebug", description = "调试调用")
        }
)
@SecurityScheme(
        securitySchemeName = NexusOpenApiSecurityNames.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "紧凑访问令牌（IDENTITY 或 BUSINESS）"
)
public final class NexmuxConsoleOpenApiDefinition {
    private NexmuxConsoleOpenApiDefinition() {
    }
}
