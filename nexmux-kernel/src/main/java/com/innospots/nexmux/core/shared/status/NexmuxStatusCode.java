package com.innospots.nexmux.core.shared.status;

import com.innospots.nexus.base.i18n.I18nObject;
import com.innospots.nexus.base.status.StatusCategory;
import com.innospots.nexus.base.status.StatusCode;
import com.innospots.nexus.base.status.StatusCodeRules;

/**
 * Nexmux 产品域状态码（模块码 {@code NMX}）。
 */
public enum NexmuxStatusCode implements StatusCode {

    RESOURCE_NOT_FOUND("0001", StatusCategory.RESOURCE_DATA,
            "Resource not found", "资源不存在",
            "Check the identifier and try again", "请检查标识后重试",
            404),
    DUPLICATE_BUSINESS_KEY("0001", StatusCategory.DATA_CONSISTENCY,
            "Duplicate business key", "业务键重复",
            "Use a different stable code or alias", "请使用其他编码或别名",
            409),
    OPERATION_PRECONDITION_FAILED("0001", StatusCategory.BUSINESS_RULE,
            "Operation precondition failed", "操作前置条件不满足",
            "Review dependency impact before continuing", "请查看依赖影响后再操作",
            422),
    SECRET_PERMISSION_DENIED("0001", StatusCategory.PERMISSION_SECURITY,
            "Secret operation denied", "密钥操作被拒绝",
            "Check nexmux client secret permission", "请检查客户端密钥权限",
            403),
    ROUTE_NOT_PUBLISHED("0002", StatusCategory.BUSINESS_RULE,
            "Route is not published", "路由未发布",
            "Publish the route before runtime consumption", "请先发布路由后再供 Runtime 使用",
            503),
    DEBUG_DISABLED("0003", StatusCategory.BUSINESS_RULE,
            "Debug invocation is disabled", "调试调用已关闭",
            "Enable debugInvokeEnabled in service settings", "请在服务设置中开启调试调用（debugInvokeEnabled）",
            403),
    DEBUG_QUOTA_EXCEEDED("0004", StatusCategory.BUSINESS_RULE,
            "Debug daily quota exhausted", "调试日配额已用尽",
            "Retry tomorrow or raise debugDailyQuota in service settings", "请明日重试或在服务设置中调高调试日配额（debugDailyQuota）",
            429),
    NO_AVAILABLE_CANDIDATE("0005", StatusCategory.BUSINESS_RULE,
            "No available candidate", "无可用候选（全部停用或不健康）",
            "Check route candidates and binding health", "请检查路由候选与 Binding 健康状态",
            503),
    ROUTE_NOT_FOUND("0002", StatusCategory.RESOURCE_DATA,
            "Route not found for alias or ingress", "别名或入口无匹配的已发布路由",
            "Check the model alias and route publish status", "请检查 model 别名与路由发布状态",
            404),
    APP_KEY_INVALID("0002", StatusCategory.PERMISSION_SECURITY,
            "AppKey is missing, invalid, disabled or expired", "AppKey 缺失、无效、已停用或已过期",
            "Provide a valid AppKey credential", "请提供有效的 AppKey 凭证",
            401),
    ROUTE_ACCESS_DENIED("0003", StatusCategory.PERMISSION_SECURITY,
            "Route is not granted to this client", "客户端未授权该路由",
            "Grant the route to the client in console", "请在控制台为客户端授权该路由",
            403),
    QUOTA_EXCEEDED("0001", StatusCategory.RESOURCE_LIMIT,
            "Client quota exceeded", "客户端配额已超限",
            "Raise the quota or wait for the next cycle", "请调高配额或等待下一周期",
            429),
    RATE_LIMITED("0002", StatusCategory.RESOURCE_LIMIT,
            "Rate limit triggered", "触发限流",
            "Slow down request rate or raise the limit", "请降低调用速率或调高限流上限",
            429),
    UPSTREAM_FAILURE("0001", StatusCategory.EXTERNAL_FAILURE,
            "Upstream provider call failed", "上游供应商调用失败",
            "Check provider availability and credential", "请检查供应商可用性与凭证",
            502),
    INVALID_REQUEST("0001", StatusCategory.INPUT_VALIDATION,
            "Invalid request", "请求参数无效",
            "Check model and messages fields", "请检查 model 与 messages 字段",
            400);

    private static final String MODULE = "NMX";

    private final String localCode;
    private final StatusCategory category;
    private final I18nObject message;
    private final I18nObject advice;
    private final int httpStatusCode;

    NexmuxStatusCode(
            String localCode,
            StatusCategory category,
            String messageEn,
            String messageZh,
            String adviceEn,
            String adviceZh,
            int httpStatusCode) {
        StatusCodeRules.requireValid(MODULE, category, localCode);
        this.localCode = localCode;
        this.category = category;
        this.message = I18nObject.of("en", messageEn, "zh", messageZh);
        this.advice = I18nObject.of("en", adviceEn, "zh", adviceZh);
        this.httpStatusCode = httpStatusCode;
    }

    @Override
    public String module() {
        return MODULE;
    }

    @Override
    public StatusCategory category() {
        return category;
    }

    @Override
    public String localCode() {
        return localCode;
    }

    @Override
    public I18nObject message() {
        return message;
    }

    @Override
    public I18nObject advice() {
        return advice;
    }

    @Override
    public int httpStatusCode() {
        return httpStatusCode;
    }
}
