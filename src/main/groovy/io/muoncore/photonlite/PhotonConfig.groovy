package io.muoncore.photonlite

import io.muoncore.Muon
import io.muoncore.MuonBuilder
import io.muoncore.codec.Codecs
import io.muoncore.codec.DelegatingCodecs
import io.muoncore.codec.avro.AvroCodec
import io.muoncore.codec.json.GsonCodec
import io.muoncore.config.AutoConfiguration
import io.muoncore.config.MuonConfigBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class PhotonConfig {

    @Value('${muon.amqp.url}')
    String amqpUrl

    @Bean
    @Profile("!test")
    Muon muon(Codecs codecs) {
        AutoConfiguration config = MuonConfigBuilder.withServiceIdentifier("photonlite")
                .withTags("eventstore")
                .addWriter({ autoConfiguration ->
            autoConfiguration.getProperties().put("amqp.transport.url", amqpUrl);
            autoConfiguration.getProperties().put("amqp.discovery.url", amqpUrl);
        }).build()

        return MuonBuilder.withConfig(config).withCodecs(codecs).build();
    }
}
