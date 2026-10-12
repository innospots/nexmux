package com.innospots.nexmux.console.endpoint.settings;

import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 服务设置（平台全局参数）端点。
 * UI 契约：GET 直出扁平设置对象（不包 R），数值键以数字返回；
 * PUT 整包提交已知键（未知键忽略），返回保存后的全量设置。
 */
@Path("/api/d/service-settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NexmuxServiceSettings", description = "平台服务设置")
@NexusAuthenticatedApi
@RequiredArgsConstructor
public class ServiceSettingsEndpoint {

    /** 以数字返回的键（对应表单 NumberInput 字段）。 */
    private static final Set<String> NUMERIC_KEYS =
            Set.of("logRetentionDays", "requestTimeoutMs", "upstreamConnectTimeoutMs");

    private final PlatformSettingService platformSettingService;

    @GET
    @Operation(operationId = "nexmuxServiceSettingsGet", summary = "查询平台服务设置")
    public Map<String, Object> get() {
        return typed(platformSettingService.getAll());
    }

    @PUT
    @Operation(operationId = "nexmuxServiceSettingsSave", summary = "保存平台服务设置")
    public Map<String, Object> save(Map<String, Object> values) {
        Map<String, String> stringValues = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) ->
                    stringValues.put(key, value == null ? null : String.valueOf(value)));
        }
        return typed(platformSettingService.save(stringValues));
    }

    private static Map<String, Object> typed(Map<String, String> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (NUMERIC_KEYS.contains(key)) {
                try {
                    out.put(key, Long.parseLong(value));
                    return;
                } catch (NumberFormatException ignored) {
                    // 非数字落库值按原串返回
                }
            }
            out.put(key, value);
        });
        return out;
    }
}
