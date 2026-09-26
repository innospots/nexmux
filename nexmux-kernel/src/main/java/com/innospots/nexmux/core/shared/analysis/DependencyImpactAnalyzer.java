package com.innospots.nexmux.core.shared.analysis;

import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import java.util.List;

public class DependencyImpactAnalyzer {

    public record ImpactReport(List<String> routeRefs, List<String> clientRefs, boolean blocking) {
    }

    public ImpactReport analyzeDisable(String resourceType, String resourceId) {
        return new ImpactReport(List.of(), List.of(), false);
    }

    public void requireSafe(ImpactReport report) {
        if (report.blocking()) {
            throw NexusException.build(NexmuxStatusCode.OPERATION_PRECONDITION_FAILED, "dependency impact blocks operation");
        }
    }
}
