package com.innospots.nexmux.core.debug.service;

import com.innospots.nexmux.core.debug.domain.request.ClientInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.request.ModelInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.vo.DebugInvokeResultVo;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DebugService {

    public DebugInvokeResultVo modelInvoke(ModelInvokeDebugRequest request) {
        return new DebugInvokeResultVo(UUID.randomUUID().toString(), "TEST", 200, "{\"ok\":true}", 32);
    }

    public DebugInvokeResultVo clientInvoke(ClientInvokeDebugRequest request) {
        return new DebugInvokeResultVo(UUID.randomUUID().toString(), "TEST", 200, "{\"ok\":true}", 48);
    }
}
