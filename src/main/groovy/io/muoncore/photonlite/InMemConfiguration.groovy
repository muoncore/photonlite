package io.muoncore.photonlite

import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@Slf4j
class InMemConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Persistence persistence() {
        log.info("Using InMemPersistence")
        return new InMemPersistence()
    }

    @Bean
    @ConditionalOnMissingBean
    ClusterMessaging clusterMessaging() {
        log.info("Cluster messaging is disabled, running in single node mode")
        new SingleNodeClusterMessaging()
    }
}
