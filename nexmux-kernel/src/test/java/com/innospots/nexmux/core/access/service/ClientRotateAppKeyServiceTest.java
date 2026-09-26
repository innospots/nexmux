package com.innospots.nexmux.core.access.service;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientRotateAppKeyServiceTest {

    private ClientDao clientDao;
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientDao = Mockito.mock(ClientDao.class);
        clientService = new ClientService(clientDao);
    }

    @Test
    void rotateIncrementsVersionAndReturnsPlainKeyOnce() {
        ClientEntity entity = new ClientEntity();
        entity.setClientId("cl-1");
        entity.setAppKeyVersion(2);
        entity.setAppKeyHash("old");
        entity.setAppKeySalt("old-salt");
        when(clientDao.selectById("cl-1")).thenReturn(entity);

        var result = clientService.rotateAppKey("cl-1");

        assertThat(result.appKeyVersion()).isEqualTo(3);
        assertThat(result.appKeyPlaintext()).isNotBlank();
        assertThat(entity.getAppKeyHash()).isNotEqualTo("old");
        verify(clientDao).updateById(entity);
    }
}
