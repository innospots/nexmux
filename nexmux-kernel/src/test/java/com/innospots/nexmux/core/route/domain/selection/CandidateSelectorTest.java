package com.innospots.nexmux.core.route.domain.selection;

import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateSelectorTest {

    private final CandidateSelector selector = new CandidateSelector();

    private static RouteCandidate candidate(String modelCode, int weight) {
        return new RouteCandidate(modelCode, weight, null, null, null, null);
    }

    private static RouteSpec spec(String strategy, List<RouteCandidate> candidates) {
        return new RouteSpec("general", "/v1/chat/completions", strategy, candidates, false, 1);
    }

    @Test
    void emptyWhenSpecOrCandidatesMissing() {
        assertThat(selector.select(null)).isEmpty();
        assertThat(selector.select(spec("FIXED", List.of()))).isEmpty();
    }

    @Test
    void fixedPicksFirstEnabledCandidate() {
        RouteSpec spec = spec("FIXED", List.of(
                new RouteCandidate("m-disabled", 100, false, null, null, null),
                candidate("m-a", 100),
                candidate("m-b", 100)));

        Optional<CandidateSelector.Selection> selection = selector.select(spec);

        assertThat(selection).isPresent();
        assertThat(selection.get().candidate().modelCode()).isEqualTo("m-a");
        assertThat(selection.get().strategyType()).isEqualTo("FIXED");
        assertThat(selection.get().poolSize()).isEqualTo(2);
    }

    @Test
    void missingStrategyDefaultsToFixed() {
        RouteSpec spec = spec(null, List.of(candidate("m-a", 1), candidate("m-b", 100)));

        assertThat(selector.select(spec)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-a");
    }

    @Test
    void weightedNeverPicksZeroWeightCandidate() {
        RouteSpec spec = spec("WEIGHTED", List.of(candidate("m-zero", 0), candidate("m-full", 100)));

        for (int i = 0; i < 50; i++) {
            assertThat(selector.select(spec)).get()
                    .extracting(s -> s.candidate().modelCode()).isEqualTo("m-full");
        }
    }

    @Test
    void weightedAllZeroFallsBackToFirst() {
        RouteSpec spec = spec("WEIGHTED", List.of(candidate("m-a", 0), candidate("m-b", 0)));

        assertThat(selector.select(spec)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-a");
    }

    @Test
    void roundRobinRotatesAcrossPool() {
        RouteSpec spec = spec("ROUND_ROBIN", List.of(candidate("m-a", 1), candidate("m-b", 1)));

        assertThat(selector.select(spec)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-a");
        assertThat(selector.select(spec)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-b");
        assertThat(selector.select(spec)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-a");
    }

    @Test
    void priorityTierRestrictsPool() {
        RouteSpec spec = spec("FIXED", List.of(
                new RouteCandidate("m-p1", 100, null, "P1", null, null),
                new RouteCandidate("m-p0", 100, null, "P0", null, null)));

        assertThat(selector.select(spec)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-p0");
    }

    @Test
    void priorityFallsToNextTierWhenBestDisabled() {
        RouteSpec spec = spec("FIXED", List.of(
                new RouteCandidate("m-p0", 100, false, "P0", null, null),
                new RouteCandidate("m-p1", 100, null, "P1", null, null)));

        assertThat(selector.select(spec)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-p1");
    }

    @Test
    void unannotatedCandidatesAreLowestTier() {
        RouteSpec spec = spec("FIXED", List.of(
                candidate("m-plain", 100),
                new RouteCandidate("m-p1", 100, null, "P1", null, null)));

        assertThat(selector.select(spec)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-p1");
    }

    @Test
    void healthFilterAppliesOnlyWhenEnabledBySpec() {
        RouteSpec filtering = new RouteSpec("general", "/v1", "FIXED",
                List.of(candidate("m-a", 1), candidate("m-b", 1)), true, 1);
        RouteSpec notFiltering = spec("FIXED", List.of(candidate("m-a", 1), candidate("m-b", 1)));

        assertThat(selector.select(filtering, Set.of("m-a"), null)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-b");
        assertThat(selector.select(filtering, Set.of("m-a"), null).get().healthFilterApplied()).isTrue();
        // spec 未开启健康过滤时，传入健康视图也不生效
        assertThat(selector.select(notFiltering, Set.of("m-a"), null)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-a");
    }

    @Test
    void emptyWhenAllCandidatesUnhealthy() {
        RouteSpec filtering = new RouteSpec("general", "/v1", "FIXED",
                List.of(candidate("m-a", 1)), true, 1);

        assertThat(selector.select(filtering, Set.of("m-a"), null)).isEmpty();
    }

    @Test
    void stickySessionPinsSameCandidateRegardlessOfStrategy() {
        RouteSpec spec = new RouteSpec("general", "/v1", "WEIGHTED",
                List.of(candidate("m-a", 50), candidate("m-b", 50)), false, 1,
                null, null, List.of(), true);

        String first = selector.select(spec, Set.of(), "session-1").get().candidate().modelCode();
        for (int i = 0; i < 20; i++) {
            assertThat(selector.select(spec, Set.of(), "session-1")).get()
                    .extracting(s -> s.candidate().modelCode()).isEqualTo(first);
        }
        assertThat(selector.select(spec, Set.of(), "session-1").get().stickyApplied()).isTrue();
        // 无会话键时粘性不生效
        assertThat(selector.select(spec, Set.of(), null).get().stickyApplied()).isFalse();
    }

    @Test
    void hashStrategyIsDeterministicPerSessionKey() {
        RouteSpec spec = spec("HASH", List.of(candidate("m-a", 1), candidate("m-b", 1)));

        String first = selector.select(spec, Set.of(), "user-42").get().candidate().modelCode();
        for (int i = 0; i < 20; i++) {
            assertThat(selector.select(spec, Set.of(), "user-42")).get()
                    .extracting(s -> s.candidate().modelCode()).isEqualTo(first);
        }
        // 无会话键时 HASH 回退 FIXED
        assertThat(selector.select(spec, Set.of(), null)).get()
                .extracting(s -> s.candidate().modelCode()).isEqualTo("m-a");
    }
}
