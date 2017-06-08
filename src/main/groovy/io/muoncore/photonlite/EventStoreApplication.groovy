package io.muoncore.photonlite

import groovy.util.logging.Slf4j
import io.muoncore.codec.Codecs
import io.muoncore.codec.DelegatingCodecs
import io.muoncore.codec.json.JsonOnlyCodecs
import io.muoncore.photonlite.h2.H2Configuration
import io.muoncore.photonlite.jgroups.JGroupsConfiguration
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication(exclude = [
        HibernateJpaAutoConfiguration,
        DataSourceAutoConfiguration
])
@Configuration
@Slf4j
class EventStoreApplication {

    static List<String> profiles = []

    static void main(args) {

        Options op = options()

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse( op, args);

        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "help", op );
            return
        }
        muonurl(cmd)
        persistencType(cmd)
        clusterType(cmd)

        System.setProperty("spring.profiles.active", profiles.join(","))

        SpringApplication.run(
                EventStoreApplication as Object[], args)
    }

    private static void persistencType(CommandLine cmd) {
        if (cmd.hasOption("type")) {
            switch (cmd.getOptionValue("type")) {
                case "mem":

                    break
                case "h2":
                default:
                    H2Configuration.processCommands(cmd, profiles)
            }
        }
    }

    private static void clusterType(CommandLine cmd) {
        if (cmd.hasOption("cluster")) {
            switch (cmd.getOptionValue("cluster")) {
                case "multicast":
                    JGroupsConfiguration.processCommands(cmd, profiles)
                    break
                case "none":
                default:
                    break
            }
        }
    }

    private static void muonurl(CommandLine cmd) {
        if (cmd.hasOption("muonurl")) {
            System.setProperty("muon.amqp.url", cmd.getOptionValue("muonurl"))
        }
    }

    static Options options() {
        Options op = new Options()
        op.addOption(Option.builder("type").hasArg().desc("Set the persistence type. One of mem,h2").build())
        op.addOption(Option.builder("cluster").hasArg().desc("Set the Cluster type. One of none,multicast").build())
        op.addOption(Option.builder("muonurl").hasArg().desc("The Muon discovery url to use. Try amqp://localhost or similar for an AMQP discovery").build())
        op.addOption(Option.builder("help").desc("show the help").build())
        H2Configuration.provideOptions(op)
        op
    }

    @Bean Codecs codecs() {
        new DelegatingCodecs().withCodecs(new JsonOnlyCodecs())
    }

}
