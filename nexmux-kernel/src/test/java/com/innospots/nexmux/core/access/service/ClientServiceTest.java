package com.innospots.nexmux.core.access.service;

import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.dao.ClientTemplateDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.access.domain.entity.ClientTemplateEntity;
import com.innospots.nexmux.core.access.domain.request.ClientCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientPageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientDetailVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ClientServiceTest {

    private ClientDao clientDao;
    private ClientTemplateDao clientTemplateDao;
    private ConfigAuditService configAuditService;
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientDao = Mockito.mock(ClientDao.class);
        clientTemplateDao = Mockito.mock(ClientTemplateDao.class);
        configAuditService = Mockito.mock(ConfigAuditService.class);
        clientService = new ClientService(clientDao, clientTemplateDao, configAuditService);
    }

    private ClientEntity client(String id, String code, String status) {
        ClientEntity entity = new ClientEntity();
        entity.setClientId(id);
        entity.setClientCode(code);
        entity.setDisplayName("移动 App");
        entity.setStatus(status);
        entity.setAppKeyHash("0123456789abcdef");
        entity.setAppKeySalt("salt");
        entity.setAppKeyVersion(1);
        entity.setAuthorizedRouteIds("[\"rt-1\"]");
        entity.setTags("[\"mobile\"]");
        return entity;
    }

    // ── getByCode ─────────────────────────────────────────────

    @Test
    void getByCodeReturnsDetailWithDerivedAppKeyId() {
        when(clientDao.selectOne(any())).thenReturn(client("cl-1", "mobile-app", "ENABLED"));

        ClientDetailVo detail = clientService.getByCode("mobile-app");

        assertThat(detail.clientCode()).isEqualTo("mobile-app");
        assertThat(detail.appKeyId()).isEqualTo("gw-ak-01234567");
        assertThat(detail.authorizedRouteIds()).containsExactly("rt-1");
        assertThat(detail.tags()).containsExactly("mobile");
    }

    @Test
    void getByCodeThrowsNotFoundWhenMissing() {
        when(clientDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> clientService.getByCode("missing"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    // ── enable / disable ──────────────────────────────────────

    @Test
    void disableSetsStatusDisabled() {
        ClientEntity entity = client("cl-1", "mobile-app", "ENABLED");
        when(clientDao.selectById("cl-1")).thenReturn(entity);

        var vo = clientService.disable("cl-1");

        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(vo.status()).isEqualTo("DISABLED");
    }

    @Test
    void updatePublishesAtomicPolicyRevision() {
        ClientEntity entity = client("cl-1", "mobile-app", "ENABLED");
        entity.setConfigRevision(1L);
        when(clientDao.selectById("cl-1")).thenReturn(entity);

        clientService.update("cl-1", new ClientUpdateRequest(
                null, "[]", "[]", null, List.of("rt-2")));

        assertThat(entity.getConfigRevision()).isEqualTo(2L);
        assertThat(entity.getAuthorizedRouteIds()).isEqualTo("[\"rt-2\"]");
        assertThat(clientService.publish("cl-1").configRevision()).isEqualTo(2L);
        Mockito.verify(configAuditService).record(
                Mockito.eq("CLIENT"), Mockito.eq("cl-1"), Mockito.eq("UPDATE"),
                Mockito.any(), Mockito.argThat(diff -> diff.contains("\"before\"") && diff.contains("\"after\"")));
    }

    // ── create（模板复制） ─────────────────────────────────────

    @Test
    void createWithDateOnlyExpiryStoresEndOfDayAtSecondPrecision() {
        // D-03：日历选 2026-10-31 须存 2026-10-31 23:59:59；H2 TIMESTAMP 微秒舍入会把
        // LocalTime.MAX（999999999 纳秒）进位到次日 00:00，故持久化统一用秒精度
        when(clientDao.selectCount(any())).thenReturn(0L);

        clientService.create(new ClientCreateRequest(
                "mobile-app", "移动 App", null, null, null, "2026-10-31", null, null));

        Mockito.verify(clientDao).insert(Mockito.<ClientEntity>argThat(e ->
                LocalDateTime.of(2026, 10, 31, 23, 59, 59).equals(e.getExpiresAt())));
    }

    @Test
    void createFromTemplateCopiesPolicyAndComputesExpiry() {
        when(clientDao.selectCount(any())).thenReturn(0L);
        ClientTemplateEntity template = new ClientTemplateEntity();
        template.setTemplateId("ct-1");
        template.setTemplateCode("team-default");
        template.setStatus("ENABLED");
        template.setAuthorizedRouteIds("[\"rt-1\",\"rt-2\"]");
        template.setQuotaSpec("[{\"name\":\"q\"}]");
        template.setRateLimitSpec("[{\"name\":\"r\"}]");
        template.setValidityType("days");
        template.setValidityDays(365);
        when(clientTemplateDao.selectOne(any())).thenReturn(template);

        var result = clientService.create(new ClientCreateRequest(
                "mobile-app", "移动 App", List.of("mobile"), "team-default", null, null, null, null));

        Mockito.verify(clientDao).insert(Mockito.<ClientEntity>argThat(e ->
                "ct-1".equals(e.getSourceTemplateId())
                        && "[\"rt-1\",\"rt-2\"]".equals(e.getAuthorizedRouteIds())
                        && "[{\"name\":\"q\"}]".equals(e.getQuotaSpec())
                        && e.getExpiresAt() != null
                        && e.getExpiresAt().isAfter(LocalDateTime.now().plusDays(364))));
        assertThat(result.appKeyPlaintext()).isNotBlank();
    }

    @Test
    void createRejectsUnknownTemplate() {
        when(clientDao.selectCount(any())).thenReturn(0L);
        when(clientTemplateDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> clientService.create(new ClientCreateRequest(
                "mobile-app", "移动 App", null, "ghost", null, null, null, null)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    // ── page 筛选 ─────────────────────────────────────────────

    @Test
    void pageFiltersTagsInMemory() {
        ClientEntity other = client("cl-2", "web-app", "ENABLED");
        other.setTags("[\"team\"]");
        when(clientDao.selectList(any())).thenReturn(List.of(
                client("cl-1", "mobile-app", "ENABLED"), other));
        when(clientDao.selectCount(any())).thenReturn(2L);

        var hit = clientService.page(new ClientPageRequest(1L, 20L, null, null, null, List.of("mobile")));
        assertThat(hit.records()).hasSize(1);
        assertThat(hit.total()).isEqualTo(1);

        var miss = clientService.page(new ClientPageRequest(1L, 20L, null, null, null, List.of("other")));
        assertThat(miss.records()).isEmpty();
    }
}
