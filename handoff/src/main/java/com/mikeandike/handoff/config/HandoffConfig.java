package com.mikeandike.handoff.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component 
public class HandoffConfig {
    private final Path configPath;

    public HandoffConfig(@Value("${handoff.config-dir}") String configDir){
        this.configPath = Path.of(configDir, "config.properties");
    }

    public Path getConfigPath() { return configPath; }
}
