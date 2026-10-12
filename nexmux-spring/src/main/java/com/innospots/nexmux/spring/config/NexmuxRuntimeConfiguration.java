package com.innospots.nexmux.spring.config;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.inbound.ingress.RuntimeIngressEndpoint;
import com.innospots.nexmux.inbound.pipeline.InvocationPipeline;
import com.innospots.nexmux.inbound.protocol.OpenAiCompatibleChatAdapter;
import com.innospots.nexmux.inbound.runtime.BindingHealthDao;
import com.innospots.nexmux.inbound.runtime.BindingHealthProber;
import com.innospots.nexmux.inbound.runtime.BindingHealthRegistry;
import com.innospots.nexmux.inbound.runtime.PublishedRouteCache;
import com.innospots.nexmux.inbound.runtime.RuntimeCatalogCache;
import com.innospots.nexmux.inbound.runtime.RuntimeSnapshotRefresher;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Model Runtime 数据面装配（ADR-0002 / nexmux-runtime-design §5.1）：
 * 仅在 {@code runtime} profile（独立 JVM 进程、独立端口）激活；
 * 与 Console 进程（{@code !runtime}）共享 artifact 与数据库实例，但装配面完全分离——
 * 本配置只产出 inbound 链路 beans 与 {@code /v1/**} Ingress，Console 管理端点（{@code /api/d/**}）
 * 在 runtime profile 下不装配。
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
                                            ProviderCredentialDao providerCredentialDao, ClientDao clientDao) {
        return new RuntimeCatalogCache(modelDao, providerDao, providerCredentialDao, clientDao);
    }

    @Bean
    BindingHealthRegistry bindingHealthRegistry(
            @Value("${nexmux.runtime.health-unhealthy-threshold:3}") int unhealthyThreshold,
            @Value("${nexmux.runtime.health-degraded-latency-ms:10000}") long degradedLatencyMs) {
        return new BindingHealthRegistry(unhealthyThreshold, degradedLatencyMs);
    }

    @Bean
    BindingHealthProber bindingHealthProber(
            RuntimeCatalogCache catalogCache, BindingHealthRegistry registry, BindingHealthDao healthDao,
            @Value("${nexmux.runtime.health-probe-timeout-ms:5000}") int probeTimeoutMs) {
        return new BindingHealthProber(catalogCache, registry, healthDao, probeTimeoutMs);
    }

    @Bean(destroyMethod = "close")
    RuntimeSnapshotRefresher runtimeSnapshotRefresher(
            PublishedRouteCache routeCache, RuntimeCatalogCache catalogCache,
            BindingHealthProber healthProber, InvocationPipeline pipeline) {
        return new RuntimeSnapshotRefresher(routeCache, catalogCache, healthProber, pipeline::resyncQuotas);
    }

    /**
     * 首轮快照/探活/配额校准在上下文就绪后执行（ApplicationRunner 晚于 spring.sql.init，
     * 避免建表完成前查库的启动竞态）。
     */
    @Bean
    org.springframework.boot.ApplicationRunner runtimeBootstrap(
            RuntimeSnapshotRefresher refresher,
            @Value("${nexmux.runtime.snapshot-poll-interval-seconds:30}") long pollIntervalSeconds,
            @Value("${nexmux.runtime.health-probe-interval-seconds:60}") long probeIntervalSeconds,
            @Value("${nexmux.runtime.quota-resync-interval-seconds:300}") long quotaResyncIntervalSeconds) {
        return args -> refresher.start(pollIntervalSeconds, probeIntervalSeconds, quotaResyncIntervalSeconds);
    }

    @Bean
    InvocationPipeline invocationPipeline(RuntimeCatalogCache catalogCache,
                                          PublishedRouteCache routeCache,
                                          BindingHealthRegistry healthRegistry,
                                          RequestLogWriter requestLogWriter,
                                          RequestLogDao requestLogDao) {
        return new InvocationPipeline(catalogCache, routeCache, healthRegistry,
                new OpenAiCompatibleChatAdapter(), requestLogWriter, requestLogDao);
    }

    @Bean
    RuntimeIngressEndpoint runtimeIngressEndpoint(InvocationPipeline pipeline) {
        return new RuntimeIngressEndpoint(pipeline);
    }
}
