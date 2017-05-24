package io.muoncore.photonlite.h2

import groovy.util.logging.Slf4j
import io.muoncore.photonlite.Persistence
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile

@Configuration
@Profile("h2")
@Import([HibernateJpaAutoConfiguration,
        DataSourceAutoConfiguration])
@Slf4j
class H2Configuration {
    @Bean
    Persistence persistence() {
        log.info "Using H2 based persistence"
        return new H2Persistence()
    }
}
