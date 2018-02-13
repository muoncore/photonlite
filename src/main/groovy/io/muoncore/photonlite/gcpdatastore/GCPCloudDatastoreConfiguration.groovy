package io.muoncore.photonlite.gcpdatastore

import groovy.util.logging.Slf4j
import io.muoncore.photonlite.Persistence
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile

@Configuration
@Profile("gcpdatastore")
@Import([])
@Slf4j
class GCPCloudDatastoreConfiguration {

    @Bean
    Persistence persistence() {
        log.info "Using GCP Datastore based persistence"
        return new GCPCloudDatastorePersistence()
    }

    static processCommands(CommandLine cmd, profiles) {
        profiles << "gcpdatastore"

//        if (cmd.hasOption("gcpdatastore")) {
//            log.info "Persisting events into Mongo at: ${cmd.getOptionValue("mongourl")}"
//            url = cmd.getOptionValue("mongourl")
//        }
    }

    static provideOptions(Options options) {
//        options.addOption(Option.builder("mongourl").hasArg().desc("set the MongoDB connection URL - https://docs.mongodb.com/manual/reference/connection-string/").build())
    }
}
