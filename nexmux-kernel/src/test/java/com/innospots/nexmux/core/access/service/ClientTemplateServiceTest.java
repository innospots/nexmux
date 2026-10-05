package com.innospots.nexmux.core.access.service;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.dao.ClientTemplateDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.access.domain.entity.ClientTemplateEntity;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplatePageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateUpdateRequest;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ClientTemplateServiceTest {

    private ClientTemplateDao clientTemplateDao;
    private ClientDao clientDao;
    private ClientTemplateService templateService;

    @BeforeEach
    void setUp() {
        clientTemplateDao = Mockito.mock(ClientTemplateDao.class);
        clientDao = Mockito.mock(ClientDao.class);
        templateService = new ClientTemplateService(clientTemplateDao, clientDao);
    }

    private ClientTemplateEntity template(String id, String code, String status) {
        ClientTemplateEntity entity = new ClientTemplateEntity();
        entity.setTemplateId(id);
        entity.setTemplateCode(code);
        entity.setDisplayName("团队默认");
        entity.setStatus(status);
        entity.setAuthorizedRouteIds("[\"rt-1\"]");
        entity.setTags("[\"team-default\"]");
        entity.setValidityType("days");
        entity.setValidityDays(365);
        return entity;
    }

    @Test
    void createWritesAllFields() {
        when(clientTemplateDao.selectCount(any())).thenReturn(0L);

        var vo = templateService.create(new ClientTemplateCreateRequest(
                "team-default", "团队默认", "团队默认策略", List.of("team-default"),
                "days", 365, null, List.of("rt-1"),
                "[{\"name\":\"q\"}]", "[{\"name\":\"r\"}]"));

        assertThat(vo.templateCode()).isEqualTo("team-default");
        Mockito.verify(clientTemplateDao).insert(Mockito.<ClientTemplateEntity>argThat(e ->
                "days".equals(e.getValidityType())
                        && Integer.valueOf(365).equals(e.getValidityDays())
                        && "ENABLED".equals(e.getStatus())));
    }

    @Test
    void createRejectsDuplicateCode() {
        when(clientTemplateDao.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> templateService.create(new ClientTemplateCreateRequest(
                "team-default", "x", null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.DUPLICATE_BUSINESS_KEY.fullCode()));
    }

    @Test
    void getByCodeReturnsDetail() {
        when(clientTemplateDao.selectOne(any())).thenReturn(template("ct-1", "team-default", "ENABLED"));

        var detail = templateService.getByCode("team-default");

        assertThat(detail.templateCode()).isEqualTo("team-default");
        assertThat(detail.validityType()).isEqualTo("days");
        assertThat(detail.validityDays()).isEqualTo(365);
        assertThat(detail.authorizedRouteIds()).containsExactly("rt-1");
    }

    @Test
    void getByCodeThrowsNotFoundWhenMissing() {
        when(clientTemplateDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> templateService.getByCode("missing"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    @Test
    void updateAppliesEditableFields() {
        ClientTemplateEntity entity = template("ct-1", "team-default", "ENABLED");
        when(clientTemplateDao.selectById("ct-1")).thenReturn(entity);

        templateService.update("ct-1", new ClientTemplateUpdateRequest(
                "团队默认（新）", "新描述", List.of("team"), "fixed", null, "2027-12-31",
                null, null, null));

        assertThat(entity.getDisplayName()).isEqualTo("团队默认（新）");
        assertThat(entity.getValidityType()).isEqualTo("fixed");
        assertThat(entity.getValidityDays()).isNull();
        assertThat(entity.getValidTo()).isNotNull();
        Mockito.verify(clientTemplateDao).updateById(entity);
    }

    @Test
    void disableSetsStatusDisabled() {
        ClientTemplateEntity entity = template("ct-1", "team-default", "ENABLED");
        when(clientTemplateDao.selectById("ct-1")).thenReturn(entity);

        var vo = templateService.disable("ct-1");

        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(vo.status()).isEqualTo("DISABLED");
    }

    @Test
    void createdClientsReturnsBySourceTemplate() {
        when(clientTemplateDao.selectById("ct-1")).thenReturn(template("ct-1", "team-default", "ENABLED"));
        ClientEntity client = new ClientEntity();
        client.setClientId("cl-1");
        client.setClientCode("mobile-app");
        client.setDisplayName("移动 App");
        client.setStatus("ENABLED");
        client.setAppKeyVersion(1);
        client.setAuthorizedRouteIds("[]");
        when(clientDao.selectList(any())).thenReturn(List.of(client));

        var clients = templateService.createdClients("ct-1");

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).clientCode()).isEqualTo("mobile-app");
    }

    @Test
    void pageFiltersKeywordAndTags() {
        when(clientTemplateDao.selectCount(any())).thenReturn(1L);
        when(clientTemplateDao.selectList(any()))
                .thenReturn(List.of(template("ct-1", "team-default", "ENABLED")));

        var hit = templateService.page(new ClientTemplatePageRequest(
                1L, 20L, null, "团队", List.of("team-default")));
        assertThat(hit.records()).hasSize(1);

        var miss = templateService.page(new ClientTemplatePageRequest(
                1L, 20L, null, null, List.of("other")));
        assertThat(miss.records()).isEmpty();
    }
}
