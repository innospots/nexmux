/**
 * 调用链路领域：OpenAI 兼容入口、调用编排、治理关口与上游协议适配。
 *
 * <p>领域内技术模块：{@code endpoint}（HTTP 边界）、{@code service}（编排、鉴权、
 * 配额与限流关口、Binding 选择）、{@code adapter}（上游协议实现）。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see com.innospots.nexmux.inbound.invocation.service.InvocationPipeline
 */
package com.innospots.nexmux.inbound.invocation;
