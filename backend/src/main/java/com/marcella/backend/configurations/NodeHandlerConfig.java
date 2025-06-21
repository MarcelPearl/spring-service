package com.marcella.backend.configurations;

import com.marcella.backend.nodeHandlers.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class NodeHandlerConfig {

    private final StartNodeHandler startNodeHandler;
    private final EmailNodeHandler emailNodeHandler;
    private final TransformNodeHandler transformNodeHandler;
    private final WebhookNodeHandler webhookNodeHandler;
    private final DelayNodeHandler delayNodeHandler;
    private final FilterNodeHandler filterNodeHandler;

    @Bean
    public List<NodeHandler> nodeHandlers() {
        return List.of(
                startNodeHandler,
                emailNodeHandler,
                transformNodeHandler,
                webhookNodeHandler,
                delayNodeHandler,
                filterNodeHandler
        );
    }
}