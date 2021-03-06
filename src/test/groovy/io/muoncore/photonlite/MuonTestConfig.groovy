package io.muoncore.photonlite

import io.muoncore.MultiTransportMuon
import io.muoncore.Muon
import io.muoncore.codec.json.JsonOnlyCodecs
import io.muoncore.config.AutoConfiguration
import io.muoncore.config.MuonConfigBuilder
import io.muoncore.memory.discovery.InMemDiscovery
import io.muoncore.memory.transport.InMemTransport
import io.muoncore.memory.transport.bus.EventBus
import io.muoncore.protocol.event.client.DefaultEventClient
import io.muoncore.protocol.event.client.EventClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("test")
@Configuration
class MuonTestConfig {


    @Bean
    InMemDiscovery discovery() {
        return new InMemDiscovery()
    }

    @Bean
    EventBus bus() {
        return new EventBus()
    }



    @Bean
    Muon muon() {
        AutoConfiguration config = MuonConfigBuilder.withServiceIdentifier("photonlite")
        .withTags("eventstore")
                .build()

        return new MultiTransportMuon(config, discovery(),
                Collections.singletonList(
                        new InMemTransport(config, bus())
                ),
                new JsonOnlyCodecs())
    }

    @Bean
    EventClient eventClient() {
        AutoConfiguration config = MuonConfigBuilder.withServiceIdentifier("client")
                .build()

        def muon2 = new MultiTransportMuon(config, discovery(),
                Collections.singletonList(
                        new InMemTransport(config, bus())
                ),
                new JsonOnlyCodecs())

        return new DefaultEventClient(muon2)
    }
}
