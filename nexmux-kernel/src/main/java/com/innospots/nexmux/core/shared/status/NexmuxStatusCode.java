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
            503);

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
