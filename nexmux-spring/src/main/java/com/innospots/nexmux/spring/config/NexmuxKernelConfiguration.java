package com.innospots.nexmux.spring.config;

import com.innospots.nexmux.console.endpoint.client.ClientEndpoint;
import com.innospots.nexmux.console.endpoint.client.ClientTemplateEndpoint;
import com.innospots.nexmux.console.endpoint.debug.DebugEndpoint;
import com.innospots.nexmux.console.endpoint.model.ModelEndpoint;
import com.innospots.nexmux.console.endpoint.ops.OpsEndpoint;
import com.innospots.nexmux.console.endpoint.ops.OpsWriteEndpoint;
import com.innospots.nexmux.console.endpoint.provider.ProviderEndpoint;
import com.innospots.nexmux.console.endpoint.provider.ProviderOptionsEndpoint;
import com.innospots.nexmux.console.endpoint.route.RouteEndpoint;
import com.innospots.nexmux.console.endpoint.stats.StatsEndpoint;
import com.innospots.nexmux.console.access.ClientTemplateViewAssembler;
import com.innospots.nexmux.console.access.ClientViewAssembler;
import com.innospots.nexmux.console.ops.CallLogViewAssembler;
import com.innospots.nexmux.console.ops.CostViewAssembler;
import com.innospots.nexmux.console.model.ModelViewAssembler;
import com.innospots.nexmux.console.stats.StatsViewAssembler;
import com.innospots.nexmux.console.route.RouteViewAssembler;
import com.innospots.nexmux.console.provider.ProviderViewAssembler;
import com.innospots.nexmux.console.sitemap.endpoint.NexmuxSitemapEndpoint;
import com.innospots.nexmux.console.sitemap.service.NexmuxSitemapService;
import com.innospots.nexus.console.sitemap.loader.SitemapConfigLoader;
import com.innospots.nexus.console.sitemap.service.SitemapMapper;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.dao.ClientTemplateDao;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.access.service.ClientTemplateService;
import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.audit.service.AuditQueryService;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.debug.service.DebugService;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.service.CostQueryService;
import com.innospots.nexmux.core.ops.service.RequestLogQueryService;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.shared.analysis.DependencyImpactAnalyzer;
import com.innospots.nexmux.core.stats.dao.StatsQueryDao;
import com.innospots.nexmux.core.stats.service.AvailabilityService;
import com.innospots.nexmux.core.stats.service.DashboardService;
import com.innospots.nexmux.core.stats.service.UsageStatsService;
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
    ProviderService providerService(
            ProviderDao providerDao, ProviderCredentialDao providerCredentialDao, ModelDao modelDao) {
        return new ProviderService(providerDao, providerCredentialDao, modelDao);
    }

    @Bean
    ModelService modelService(ModelDao modelDao, ProviderDao providerDao) {
        return new ModelService(modelDao, providerDao);
    }

    @Bean
    RouteService routeService(
            RouteDao routeDao, ConfigAuditService configAuditService, DependencyImpactAnalyzer analyzer) {
        return new RouteService(routeDao, configAuditService, analyzer);
    }

    @Bean
    ClientService clientService(ClientDao clientDao, ClientTemplateDao clientTemplateDao) {
        return new ClientService(clientDao, clientTemplateDao);
    }

    @Bean
    ClientTemplateService clientTemplateService(ClientTemplateDao clientTemplateDao, ClientDao clientDao) {
        return new ClientTemplateService(clientTemplateDao, clientDao);
    }

    @Bean
    RequestLogQueryService requestLogQueryService(RequestLogDao requestLogDao) {
        return new RequestLogQueryService(requestLogDao);
    }

    @Bean
    CostQueryService costQueryService(RequestLogDao requestLogDao) {
        return new CostQueryService(requestLogDao);
    }

    @Bean
    DashboardService dashboardService(StatsQueryDao statsQueryDao) {
        return new DashboardService(statsQueryDao);
    }

    @Bean
    UsageStatsService usageStatsService(StatsQueryDao statsQueryDao) {
        return new UsageStatsService(statsQueryDao);
    }

    @Bean
    AvailabilityService availabilityService(StatsQueryDao statsQueryDao) {
        return new AvailabilityService(statsQueryDao);
    }

    @Bean
    AuditQueryService auditQueryService(ConfigAuditLogDao configAuditLogDao) {
        return new AuditQueryService(configAuditLogDao);
    }

    @Bean
    DebugService debugService(
            ModelDao modelDao,
            ProviderDao providerDao,
            ProviderCredentialDao providerCredentialDao,
            ClientDao clientDao,
            RouteDao routeDao,
            ConfigAuditService configAuditService) {
        return new DebugService(
                modelDao, providerDao, providerCredentialDao, clientDao, routeDao, configAuditService);
    }

    @Bean
    ProviderViewAssembler providerViewAssembler(
            ProviderService providerService, ModelService modelService, RouteService routeService) {
        return new ProviderViewAssembler(providerService, modelService, routeService);
    }

    @Bean
    ModelViewAssembler modelViewAssembler(
            ModelService modelService, ProviderService providerService, RouteService routeService) {
        return new ModelViewAssembler(modelService, providerService, routeService);
    }

    @Bean
    @Lazy
    ProviderEndpoint providerEndpoint(
            ProviderService providerService, ProviderViewAssembler providerViewAssembler) {
        return new ProviderEndpoint(providerService, providerViewAssembler);
    }

    @Bean
    @Lazy
    ProviderOptionsEndpoint providerOptionsEndpoint(ProviderViewAssembler providerViewAssembler) {
        return new ProviderOptionsEndpoint(providerViewAssembler);
    }

    @Bean
    @Lazy
    ModelEndpoint modelEndpoint(ModelService modelService, ModelViewAssembler modelViewAssembler) {
        return new ModelEndpoint(modelService, modelViewAssembler);
    }

    @Bean
    RouteViewAssembler routeViewAssembler(RouteService routeService, ModelService modelService) {
        return new RouteViewAssembler(routeService, modelService);
    }

    @Bean
    @Lazy
    RouteEndpoint routeEndpoint(RouteService routeService, RouteViewAssembler routeViewAssembler) {
        return new RouteEndpoint(routeService, routeViewAssembler);
    }

    @Bean
    ClientViewAssembler clientViewAssembler(
            ClientService clientService, RouteService routeService,
            ClientTemplateService clientTemplateService) {
        return new ClientViewAssembler(clientService, routeService, clientTemplateService);
    }

    @Bean
    ClientTemplateViewAssembler clientTemplateViewAssembler(
            ClientTemplateService clientTemplateService, RouteService routeService,
            ClientViewAssembler clientViewAssembler) {
        return new ClientTemplateViewAssembler(
                clientTemplateService, routeService, clientViewAssembler);
    }

    @Bean
    @Lazy
    ClientEndpoint clientEndpoint(ClientService clientService, ClientViewAssembler clientViewAssembler) {
        return new ClientEndpoint(clientService, clientViewAssembler);
    }

    @Bean
    @Lazy
    ClientTemplateEndpoint clientTemplateEndpoint(
            ClientTemplateService clientTemplateService,
            ClientTemplateViewAssembler clientTemplateViewAssembler) {
        return new ClientTemplateEndpoint(clientTemplateService, clientTemplateViewAssembler);
    }

    @Bean
    CallLogViewAssembler callLogViewAssembler(
            RequestLogQueryService requestLogQueryService, ClientService clientService,
            RouteService routeService, ModelService modelService, ProviderService providerService) {
        return new CallLogViewAssembler(
                requestLogQueryService, clientService, routeService, modelService, providerService);
    }

    @Bean
    CostViewAssembler costViewAssembler(
            CostQueryService costQueryService, ClientService clientService,
            ModelService modelService, ProviderService providerService) {
        return new CostViewAssembler(costQueryService, clientService, modelService, providerService);
    }

    @Bean
    @Lazy
    OpsEndpoint opsEndpoint(
            AuditQueryService auditQueryService,
            CallLogViewAssembler callLogViewAssembler,
            CostViewAssembler costViewAssembler) {
        return new OpsEndpoint(auditQueryService, callLogViewAssembler, costViewAssembler);
    }

    @Bean
    @Lazy
    OpsWriteEndpoint opsWriteEndpoint(
            ProviderService providerService, ModelService modelService, RouteService routeService) {
        return new OpsWriteEndpoint(providerService, modelService, routeService);
    }

    @Bean
    @Lazy
    DebugEndpoint debugEndpoint(DebugService debugService) {
        return new DebugEndpoint(debugService);
    }

    @Bean
    StatsViewAssembler statsViewAssembler(
            DashboardService dashboardService, UsageStatsService usageStatsService,
            AvailabilityService availabilityService, ModelService modelService,
            ProviderService providerService, ClientService clientService, RouteService routeService) {
        return new StatsViewAssembler(dashboardService, usageStatsService, availabilityService,
                modelService, providerService, clientService, routeService);
    }

    @Bean
    @Lazy
    StatsEndpoint statsEndpoint(StatsViewAssembler statsViewAssembler) {
        return new StatsEndpoint(statsViewAssembler);
    }

    @Bean
    NexmuxSitemapService nexmuxSitemapService(SitemapConfigLoader sitemapConfigLoader) {
        return new NexmuxSitemapService(sitemapConfigLoader, new SitemapMapper());
    }

    @Bean
    @Lazy
    NexmuxSitemapEndpoint nexmuxSitemapEndpoint(NexmuxSitemapService nexmuxSitemapService) {
        return new NexmuxSitemapEndpoint(nexmuxSitemapService);
    }
}
