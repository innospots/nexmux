package com.innospots.nexmux.spring.config;

import com.innospots.nexmux.console.endpoint.client.ClientEndpoint;
import com.innospots.nexmux.console.endpoint.debug.DebugEndpoint;
import com.innospots.nexmux.console.endpoint.model.ModelEndpoint;
import com.innospots.nexmux.console.endpoint.ops.OpsEndpoint;
import com.innospots.nexmux.console.endpoint.provider.ProviderEndpoint;
import com.innospots.nexmux.console.endpoint.route.RouteEndpoint;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.audit.service.AuditQueryService;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.debug.service.DebugService;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.service.RequestLogQueryService;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.shared.analysis.DependencyImpactAnalyzer;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@MapperScan(
        basePackages = "com.innospots.nexmux.core",
        annotationClass = Mapper.class,
        sqlSessionFactoryRef = "sqlSessionFactory")
public class NexmuxKernelConfiguration {

    @Bean
    DependencyImpactAnalyzer dependencyImpactAnalyzer() {
        return new DependencyImpactAnalyzer();
    }

    @Bean
    ConfigAuditService configAuditService(ConfigAuditLogDao configAuditLogDao) {
        return new ConfigAuditService(configAuditLogDao);
    }

    @Bean
    ProviderService providerService(ProviderDao providerDao, ProviderCredentialDao providerCredentialDao) {
        return new ProviderService(providerDao, providerCredentialDao);
    }

    @Bean
    ModelService modelService(ModelDao modelDao) {
        return new ModelService(modelDao);
    }

    @Bean
    RouteService routeService(
            RouteDao routeDao, ConfigAuditService configAuditService, DependencyImpactAnalyzer analyzer) {
        return new RouteService(routeDao, configAuditService, analyzer);
    }

    @Bean
    ClientService clientService(ClientDao clientDao) {
        return new ClientService(clientDao);
    }

    @Bean
    RequestLogQueryService requestLogQueryService(RequestLogDao requestLogDao) {
        return new RequestLogQueryService(requestLogDao);
    }

    @Bean
    AuditQueryService auditQueryService(ConfigAuditLogDao configAuditLogDao) {
        return new AuditQueryService(configAuditLogDao);
    }

    @Bean
    DebugService debugService() {
        return new DebugService();
    }

    @Bean
    @Lazy
    ProviderEndpoint providerEndpoint(ProviderService providerService) {
        return new ProviderEndpoint(providerService);
    }

    @Bean
    @Lazy
    ModelEndpoint modelEndpoint(ModelService modelService) {
        return new ModelEndpoint(modelService);
    }

    @Bean
    @Lazy
    RouteEndpoint routeEndpoint(RouteService routeService) {
        return new RouteEndpoint(routeService);
    }

    @Bean
    @Lazy
    ClientEndpoint clientEndpoint(ClientService clientService) {
        return new ClientEndpoint(clientService);
    }

    @Bean
    @Lazy
    OpsEndpoint opsEndpoint(RequestLogQueryService requestLogQueryService, AuditQueryService auditQueryService) {
        return new OpsEndpoint(requestLogQueryService, auditQueryService);
    }

    @Bean
    @Lazy
    DebugEndpoint debugEndpoint(DebugService debugService) {
        return new DebugEndpoint(debugService);
    }
}
