package com.innospots.nexmux.inbound.runtime;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runtime 配置目录缓存（nexmux-runtime-design §5.3）：模型 / 供应商 / 上游凭证 / 客户端
 * 的只读内存视图，由 {@link RuntimeSnapshotRefresher} 周期性全量重载；
 * 请求链路不逐次查库（ADR-0001 §4）。
 *
 * <p>凭证明文只存在于本缓存与协议适配层（数据面职责），不出 Runtime 进程。
 */
public final class RuntimeCatalogCache {

    private final ModelDao modelDao;
    private final ProviderDao providerDao;
    private final ProviderCredentialDao providerCredentialDao;
    private final ClientDao clientDao;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    public RuntimeCatalogCache(ModelDao modelDao, ProviderDao providerDao,
                               ProviderCredentialDao providerCredentialDao, ClientDao clientDao) {
        this.modelDao = modelDao;
        this.providerDao = providerDao;
        this.providerCredentialDao = providerCredentialDao;
        this.clientDao = clientDao;
    }

    /** 全量重载（任一源失败整体抛错，调用方保留旧快照）。 */
    public void refresh() {
        List<ModelEntity> models = modelDao.selectList(null);
        List<ProviderEntity> providers = providerDao.selectList(null);
        List<ProviderCredentialEntity> credentials = providerCredentialDao.selectList(null);
        List<ClientEntity> clients = clientDao.selectList(null);
        snapshot.set(new Snapshot(
                models.stream().collect(Collectors.toUnmodifiableMap(
                        ModelEntity::getModelCode, Function.identity(), (a, b) -> a)),
                providers.stream().collect(Collectors.toUnmodifiableMap(
                        ProviderEntity::getProviderId, Function.identity(), (a, b) -> a)),
                credentials.stream().collect(Collectors.toUnmodifiableMap(
                        ProviderCredentialEntity::getProviderId, Function.identity(), (a, b) -> a)),
                List.copyOf(clients)));
    }

    public Optional<ModelEntity> findModel(String modelCode) {
        return Optional.ofNullable(snapshot.get().modelsByCode().get(modelCode));
    }

    public Optional<ProviderEntity> findProvider(String providerId) {
        return Optional.ofNullable(snapshot.get().providersById().get(providerId));
    }

    public Optional<ProviderCredentialEntity> findCredential(String providerId) {
        return Optional.ofNullable(snapshot.get().credentialsByProviderId().get(providerId));
    }

    /** 全部客户端（AppKey 校验遍历用；P0 规模下线性匹配）。 */
    public List<ClientEntity> clients() {
        return snapshot.get().clients();
    }

    /** 全部模型（探活目标收集等全量遍历用）。 */
    public List<ModelEntity> models() {
        return List.copyOf(snapshot.get().modelsByCode().values());
    }

    private record Snapshot(Map<String, ModelEntity> modelsByCode,
                            Map<String, ProviderEntity> providersById,
                            Map<String, ProviderCredentialEntity> credentialsByProviderId,
                            List<ClientEntity> clients) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), List.of());
    }
}
