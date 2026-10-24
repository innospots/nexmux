package com.innospots.nexmux.spring.config;

import com.innospots.nexmux.console.endpoint.client.ClientEndpoint;
import com.innospots.nexmux.console.endpoint.client.ClientTemplateEndpoint;
import com.innospots.nexmux.console.endpoint.debug.DebugEndpoint;
import com.innospots.nexmux.console.endpoint.model.ModelEndpoint;
import com.innospots.nexmux.console.endpoint.model.ModelOptionsEndpoint;
import com.innospots.nexmux.console.endpoint.ops.OpsEndpoint;
import com.innospots.nexmux.console.endpoint.ops.OpsWriteEndpoint;
import com.innospots.nexmux.console.endpoint.provider.ProviderEndpoint;
import com.innospots.nexmux.console.endpoint.provider.ProviderOptionsEndpoint;
import com.innospots.nexmux.console.endpoint.route.RouteEndpoint;
import com.innospots.nexmux.console.endpoint.settings.ServiceSettingsEndpoint;
import com.innospots.nexmux.console.endpoint.stats.StatsEndpoint;
import com.innospots.nexmux.console.access.ClientTemplateViewAssembler;
import com.innospots.nexmux.console.audit.AuditViewMapper;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.console.access.ClientViewAssembler;
import com.innospots.nexmux.console.ops.CallLogViewAssembler;
import com.innospots.nexmux.console.ops.CostViewAssembler;
import com.innospots.nexmux.console.model.ModelViewAssembler;
import com.innospots.nexmux.console.stats.StatsViewAssembler;
import com.innospots.nexmux.console.route.RouteViewAssembler;
import com.innospots.nexmux.console.provider.ProviderViewAssembler;
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
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.settings.dao.PlatformSettingDao;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
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
import org.springframework.context.annotation.Profile;

@Configuration
@MapperScan(
        basePackages = "com.innospots.nexmux.core",
        annotationClass = Mapper.class,
        sqlSessionFactoryRef = "sqlSessionFactory")
public class NexmuxKernelConfiguration {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(NexmuxKernelConfiguration.class);

    @Bean
    com.innospots.nexmux.core.shared.security.CredentialCipher credentialCipher() {
        com.innospots.nexmux.core.shared.security.CredentialCipher cipher =
                com.innospots.nexmux.core.shared.security.CredentialCipher.fromEnvironment();
        if (!cipher.enabled()) {
            log.warn("NEXMUX_CREDENTIAL_SECRET not configured: provider credentials are stored as plaintext");
        }
        return cipher;
    }

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
            ProviderDao providerDao, ProviderCredentialDao providerCredentialDao, ModelDao modelDao,
            ConfigAuditService configAuditService,
            com.innospots.nexmux.core.shared.security.CredentialCipher credentialCipher) {
        return new ProviderService(providerDao, providerCredentialDao, modelDao, configAuditService,
                credentialCipher);
    }

    @Bean
    ModelService modelService(ModelDao modelDao, ProviderDao providerDao,
            ConfigAuditService configAuditService) {
        return new ModelService(modelDao, providerDao, configAuditService);
    }

    @Bean
    RouteService routeService(
            RouteDao routeDao, ConfigAuditService configAuditService, DependencyImpactAnalyzer analyzer) {
        return new RouteService(routeDao, configAuditService, analyzer);
    }

    @Bean
    ClientService clientService(ClientDao clientDao, ClientTemplateDao clientTemplateDao,
            ConfigAuditService configAuditService) {
        return new ClientService(clientDao, clientTemplateDao, configAuditService);
    }

    @Bean
    ClientTemplateService clientTemplateService(ClientTemplateDao clientTemplateDao, ClientDao clientDao,
            ConfigAuditService configAuditService) {
        return new ClientTemplateService(clientTemplateDao, clientDao, configAuditService);
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
    PlatformSettingService platformSettingService(
            PlatformSettingDao platformSettingDao, ConfigAuditService configAuditService) {
        return new PlatformSettingService(platformSettingDao, configAuditService);
    }

    @Bean
    DisplayFormatsProvider displayFormatsProvider(PlatformSettingService platformSettingService) {
        return new DisplayFormatsProvider(platformSettingService);
    }

    @Bean
    AuditViewMapper auditViewMapper(DisplayFormatsProvider displayFormatsProvider) {
        return new AuditViewMapper(displayFormatsProvider);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    ServiceSettingsEndpoint serviceSettingsEndpoint(PlatformSettingService platformSettingService) {
        return new ServiceSettingsEndpoint(platformSettingService);
    }

    @Bean
    RequestLogWriter requestLogWriter(RequestLogDao requestLogDao,
            PlatformSettingService platformSettingService) {
        return new RequestLogWriter(requestLogDao, platformSettingService);
    }

    @Bean
    DebugService debugService(
            ModelDao modelDao,
            ProviderDao providerDao,
            ProviderCredentialDao providerCredentialDao,
            ClientDao clientDao,
            RouteDao routeDao,
            ConfigAuditService configAuditService,
            PlatformSettingService platformSettingService,
            com.innospots.nexmux.core.ops.dao.RequestLogDao requestLogDao,
            RequestLogWriter requestLogWriter,
            com.innospots.nexmux.core.shared.security.CredentialCipher credentialCipher) {
        return new DebugService(
                modelDao, providerDao, providerCredentialDao, clientDao, routeDao, configAuditService,
                platformSettingService, requestLogDao, requestLogWriter, credentialCipher);
    }

    @Bean
    ProviderViewAssembler providerViewAssembler(
            ProviderService providerService, ModelService modelService, RouteService routeService,
            DisplayFormatsProvider displayFormatsProvider) {
        return new ProviderViewAssembler(providerService, modelService, routeService, displayFormatsProvider);
    }

    @Bean
    ModelViewAssembler modelViewAssembler(
            ModelService modelService, ProviderService providerService, RouteService routeService,
            DisplayFormatsProvider displayFormatsProvider) {
        return new ModelViewAssembler(modelService, providerService, routeService, displayFormatsProvider);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    ProviderEndpoint providerEndpoint(
            ProviderService providerService, ProviderViewAssembler providerViewAssembler) {
        return new ProviderEndpoint(providerService, providerViewAssembler);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    ProviderOptionsEndpoint providerOptionsEndpoint(ProviderViewAssembler providerViewAssembler) {
        return new ProviderOptionsEndpoint(providerViewAssembler);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    ModelEndpoint modelEndpoint(ModelService modelService, ModelViewAssembler modelViewAssembler) {
        return new ModelEndpoint(modelService, modelViewAssembler);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    ModelOptionsEndpoint modelOptionsEndpoint(ModelViewAssembler modelViewAssembler) {
        return new ModelOptionsEndpoint(modelViewAssembler);
    }

    @Bean
    RouteViewAssembler routeViewAssembler(
            RouteService routeService, ModelService modelService,
            DisplayFormatsProvider displayFormatsProvider) {
        return new RouteViewAssembler(routeService, modelService, displayFormatsProvider);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    RouteEndpoint routeEndpoint(RouteService routeService, RouteViewAssembler routeViewAssembler) {
        return new RouteEndpoint(routeService, routeViewAssembler);
    }

    @Bean
    ClientViewAssembler clientViewAssembler(
            ClientService clientService, RouteService routeService,
            ClientTemplateService clientTemplateService,
            DisplayFormatsProvider displayFormatsProvider) {
        return new ClientViewAssembler(clientService, routeService, clientTemplateService,
                displayFormatsProvider);
    }

    @Bean
    ClientTemplateViewAssembler clientTemplateViewAssembler(
            ClientTemplateService clientTemplateService, RouteService routeService,
            ClientViewAssembler clientViewAssembler, DisplayFormatsProvider displayFormatsProvider) {
        return new ClientTemplateViewAssembler(
                clientTemplateService, routeService, clientViewAssembler, displayFormatsProvider);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    ClientEndpoint clientEndpoint(ClientService clientService, ClientViewAssembler clientViewAssembler) {
        return new ClientEndpoint(clientService, clientViewAssembler);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    ClientTemplateEndpoint clientTemplateEndpoint(
            ClientTemplateService clientTemplateService,
            ClientTemplateViewAssembler clientTemplateViewAssembler) {
        return new ClientTemplateEndpoint(clientTemplateService, clientTemplateViewAssembler);
    }

    @Bean
    CallLogViewAssembler callLogViewAssembler(
            RequestLogQueryService requestLogQueryService, ClientService clientService,
            RouteService routeService, ModelService modelService, ProviderService providerService,
            DisplayFormatsProvider displayFormatsProvider) {
        return new CallLogViewAssembler(
                requestLogQueryService, clientService, routeService, modelService, providerService,
                displayFormatsProvider);
    }

    @Bean
    CostViewAssembler costViewAssembler(
            CostQueryService costQueryService, ClientService clientService,
            ModelService modelService, ProviderService providerService,
            DisplayFormatsProvider displayFormatsProvider) {
        return new CostViewAssembler(costQueryService, clientService, modelService, providerService,
                displayFormatsProvider);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    OpsEndpoint opsEndpoint(
            AuditQueryService auditQueryService,
            AuditViewMapper auditViewMapper,
            CallLogViewAssembler callLogViewAssembler,
            CostViewAssembler costViewAssembler) {
        return new OpsEndpoint(auditQueryService, auditViewMapper, callLogViewAssembler, costViewAssembler);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    OpsWriteEndpoint opsWriteEndpoint(
            ProviderService providerService, ModelService modelService, RouteService routeService) {
        return new OpsWriteEndpoint(providerService, modelService, routeService);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    DebugEndpoint debugEndpoint(DebugService debugService) {
        return new DebugEndpoint(debugService);
    }

    @Bean
    StatsViewAssembler statsViewAssembler(
            DashboardService dashboardService, UsageStatsService usageStatsService,
            AvailabilityService availabilityService, ModelService modelService,
            ProviderService providerService, ClientService clientService, RouteService routeService,
            DisplayFormatsProvider displayFormatsProvider) {
        return new StatsViewAssembler(dashboardService, usageStatsService, availabilityService,
                modelService, providerService, clientService, routeService, displayFormatsProvider);
    }

    @Bean
    @Lazy
    @Profile("!runtime")
    StatsEndpoint statsEndpoint(StatsViewAssembler statsViewAssembler) {
        return new StatsEndpoint(statsViewAssembler);
    }
}
