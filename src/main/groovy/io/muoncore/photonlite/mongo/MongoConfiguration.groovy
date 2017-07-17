package io.muoncore.photonlite.mongo

import com.mongodb.Mongo
import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import groovy.util.logging.Slf4j
import io.muoncore.photonlite.Persistence
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.config.AbstractMongoConfiguration
import org.springframework.data.mongodb.core.MongoTemplate

@Configuration
@Profile("mongo")
@Import([])
@Slf4j
class MongoConfiguration extends AbstractMongoConfiguration {

    static String url = "mongodb://localhost"

    @Bean
    Persistence persistence(MongoTemplate template, MongoEventRepo repo) {
        log.info "Using Mongo based persistence"
        def p = new MongoPersistence(repo: repo, template: template)
        p.prepareIndex()
        p
    }

    static processCommands(CommandLine cmd, profiles) {
        profiles << "mongo"

        if (cmd.hasOption("mongourl")) {
            log.info "Persisting events into Mongo at: ${cmd.getOptionValue("mongourl")}"
            url = cmd.getOptionValue("mongourl")
        }
    }

    static provideOptions(Options options) {
        options.addOption(Option.builder("mongourl").hasArg().desc("set the MongoDB connection URL - https://docs.mongodb.com/manual/reference/connection-string/").build())
    }

    @Override
    protected String getDatabaseName() {
        return "photon-events"
    }

    @Override
    Mongo mongo() throws Exception {
        log.info("Connecting to MongoDB at $url")
        return new MongoClient(new MongoClientURI(url))
    }
}
