package com.innospots.nexmux.spring.config;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.health.dao.BindingHealthDao;
import com.innospots.nexmux.core.health.operator.BindingHealthOperator;
import com.innospots.nexmux.core.health.service.BindingHealthRegistry;
import com.innospots.nexmux.core.health.service.BindingHealthService;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import com.innospots.nexmux.core.shared.security.CredentialCipher;
import com.innospots.nexmux.inbound.catalog.service.PublishedRouteCache;
import com.innospots.nexmux.inbound.catalog.service.RuntimeCatalogCache;
import com.innospots.nexmux.inbound.health.service.BindingHealthProber;
import com.innospots.nexmux.inbound.invocation.adapter.OpenAiCompatibleChatAdapter;
import com.innospots.nexmux.inbound.invocation.endpoint.RuntimeIngressEndpoint;
import com.innospots.nexmux.inbound.invocation.service.InvocationPipeline;
import com.innospots.nexmux.inbound.retention.service.RequestLogRetentionCleaner;
import com.innospots.nexmux.inbound.runtime.RuntimeSnapshotRefresher;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Model Runtime 数据面装配（ADR-0002）。
 *
 * <p>仅在 {@code runtime} profile（独立进程、独立端口）激活；与 Console 进程共享 artifact
 * 与数据库实例，但装配面完全分离——本配置只产出 inbound 链路 beans 与 {@code /v1/**} 入口，
 * Console 管理端点（{@code /api/d/**}）在 runtime profile 下不装配。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see InvocationPipeline
 * @see RuntimeIngressEndpoint
 */
@Configuration
@Profile("runtime")
@MapperScan(
        basePackages = "com.innospots.nexmux.inbound",
        annotationClass = Mapper.class,
        sqlSessionFactoryRef = "sqlSessionFactory")
public class NexmuxRuntimeConfiguration {

    @Bean
    PublishedRouteCache publishedRouteCache(RouteDao routeDao) {
        return new PublishedRouteCache(routeDao);
    }

    @Bean
    RuntimeCatalogCache runtimeCatalogCache(ModelDao modelDao, ProviderDao providerDao,
                                            ProviderCredentialDao providerCredentialDao,
                                            ClientDao clientDao) {
        return new RuntimeCatalogCache(modelDao, providerDao, providerCredentialDao, clientDao);
    }

    @Bean
    BindingHealthOperator bindingHealthOperator(BindingHealthDao bindingHealthDao) {
        return new BindingHealthOperator(bindingHealthDao);
    }

    @Bean
    BindingHealthRegistry bindingHealthRegistry(
            @Value("${nexmux.runtime.health-unhealthy-threshold:3}") int unhealthyThreshold,
            @Value("${nexmux.runtime.health-degraded-latency-ms:10000}") long degradedLatencyMs) {
        return new BindingHealthRegistry(unhealthyThreshold, degradedLatencyMs);
    }

    @Bean
    BindingHealthService bindingHealthService(BindingHealthRegistry bindingHealthRegistry,
                                              BindingHealthOperator bindingHealthOperator) {
        return new BindingHealthService(bindingHealthRegistry, bindingHealthOperator);
    }

    @Bean
    BindingHealthProber bindingHealthProber(
            RuntimeCatalogCache runtimeCatalogCache,
            BindingHealthService bindingHealthService,
            CredentialCipher credentialCipher,
            @Value("${nexmux.runtime.health-probe-timeout-ms:5000}") int probeTimeoutMillis) {
        return new BindingHealthProber(runtimeCatalogCache, bindingHealthService,
                credentialCipher, probeTimeoutMillis);
    }

    @Bean
    InvocationPipeline invocationPipeline(RuntimeCatalogCache runtimeCatalogCache,
                                          PublishedRouteCache publishedRouteCache,
                                          BindingHealthRegistry bindingHealthRegistry,
                                          RequestLogWriter requestLogWriter,
                                          RequestLogDao requestLogDao,
                                          PlatformSettingService platformSettingService,
                                          CredentialCipher credentialCipher) {
        return InvocationPipeline.of(runtimeCatalogCache, publishedRouteCache,
                bindingHealthRegistry, new OpenAiCompatibleChatAdapter(), requestLogWriter,
                requestLogDao, platformSettingService, credentialCipher);
    }

    @Bean(destroyMethod = "close")
    RuntimeSnapshotRefresher runtimeSnapshotRefresher(
            PublishedRouteCache publishedRouteCache, RuntimeCatalogCache runtimeCatalogCache,
            BindingHealthProber bindingHealthProber, InvocationPipeline invocationPipeline) {
        return new RuntimeSnapshotRefresher(publishedRouteCache, runtimeCatalogCache,
                bindingHealthProber, invocationPipeline::resyncQuotas);
    }

    /**
     * 调用日志保留清理：按服务设置 {@code logRetentionDays} 每日清理超期行；
     * 启动随 bootstrap（避让建表竞态）。
     */
    @Bean(destroyMethod = "close")
    RequestLogRetentionCleaner requestLogRetentionCleaner(
            RequestLogDao requestLogDao, PlatformSettingService platformSettingService,
            @Value("${nexmux.runtime.log-retention-initial-delay-minutes:10}") long initialDelayMinutes,
            @Value("${nexmux.runtime.log-retention-period-minutes:1440}") long periodMinutes) {
        return new RequestLogRetentionCleaner(requestLogDao, platformSettingService,
                initialDelayMinutes, periodMinutes);
    }

    /**
     * 首轮快照/探活/配额校准在上下文就绪后执行（晚于 {@code spring.sql.init}，
     * 避免建表完成前查库的启动竞态）。
     */
    @Bean
    ApplicationRunner runtimeBootstrap(
            RuntimeSnapshotRefresher refresher,
            RequestLogRetentionCleaner retentionCleaner,
            @Value("${nexmux.runtime.snapshot-poll-interval-seconds:30}") long pollIntervalSeconds,
            @Value("${nexmux.runtime.health-probe-interval-seconds:60}") long probeIntervalSeconds,
            @Value("${nexmux.runtime.quota-resync-interval-seconds:300}")
            long quotaResyncIntervalSeconds) {
        return args -> {
            refresher.start(pollIntervalSeconds, probeIntervalSeconds, quotaResyncIntervalSeconds);
            retentionCleaner.start();
        };
    }

    @Bean
    RuntimeIngressEndpoint runtimeIngressEndpoint(InvocationPipeline invocationPipeline) {
        return new RuntimeIngressEndpoint(invocationPipeline);
    }
}
