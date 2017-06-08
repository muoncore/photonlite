package io.muoncore.photonlite.h2

import groovy.util.logging.Slf4j
import io.muoncore.photonlite.Persistence
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.springframework.beans.factory.annotation.Value
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

    static processCommands(CommandLine cmd, profiles) {
        profiles << "h2"

        if (cmd.hasOption("h2path")) {
            log.info "Persisting events into H2 filedb at ${cmd.getOptionValue("h2path")}"
            System.setProperty("spring.datasource.url", "jdbc:h2:file:${cmd.getOptionValue("h2path")}")
        }
    }

    static provideOptions(Options options) {
        options.addOption(Option.builder("h2path").hasArg().desc("set the location of the h2 database").build())
    }
}
