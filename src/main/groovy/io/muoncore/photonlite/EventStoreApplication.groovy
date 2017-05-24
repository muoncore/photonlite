package io.muoncore.photonlite

import io.muoncore.codec.Codecs
import io.muoncore.codec.DelegatingCodecs
import io.muoncore.codec.avro.AvroCodec
import io.muoncore.codec.json.GsonCodec
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
class EventStoreApplication {

    static void main(args) {

        Options op = options()

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse( op, args);

        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "help", op );
            return;
        }
        if (cmd.hasOption("type")) {
            switch(cmd.getOptionValue("type")) {
                case "mem":

                    break
                case "h2":
                default:
                    System.setProperty("spring.profiles.active", "h2")
            }
        }

        SpringApplication.run(
                EventStoreApplication as Object[], args)
    }

    static Options options() {
        Options op = new Options()
        op.addOption(Option.builder("type").hasArg().desc("Set the persistence type. One of mem,h2").build())
        op.addOption(Option.builder("help").desc("show the help").build())
        op
    }

    @Bean Codecs codecs() {
        new DelegatingCodecs().withCodec(new AvroCodec()).withCodec(new GsonCodec())
    }

}
