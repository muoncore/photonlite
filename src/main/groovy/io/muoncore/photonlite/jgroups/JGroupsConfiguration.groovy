package io.muoncore.photonlite.jgroups

import groovy.util.logging.Slf4j
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options

@Slf4j
class JGroupsConfiguration {




    static processCommands(CommandLine cmd, profiles) {
        profiles << "jgroups"

        //TODO, sort out jgroups tcp vs udp etc.
    }

    static provideOptions(Options options) {
//        options.addOption(Option.builder("h2path").hasArg().desc("set the location of the h2 database").build())
    }
}
