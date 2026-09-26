package com.innospots.nexmux.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.innospots.nexmux.spring.config.NexmuxKernelConfiguration;
import com.innospots.nexus.spring.platform.EnableNexusPlatform;

/**
 * Nexmux 运营平台控制台可执行入口。
 */
@SpringBootApplication
@EnableNexusPlatform
@Import(NexmuxKernelConfiguration.class)
public class NexmuxPlatformServer {

    /**
     * 启动运营平台控制台。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NexmuxPlatformServer.class, args);
    }
}
