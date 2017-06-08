package io.muoncore.photonlite.jgroups

import io.muoncore.codec.Codecs
import io.muoncore.codec.json.JsonOnlyCodecs
import io.muoncore.photonlite.ClusterMessaging
import io.muoncore.photonlite.EventDistribution
import io.muoncore.protocol.event.Event
import org.jgroups.JChannel
import org.jgroups.Message
import org.jgroups.Receiver
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("jgroups")
class JGroupsCluster implements ClusterMessaging {

    JChannel channel

    Codecs codecs = new JsonOnlyCodecs()

    JGroupsCluster() {
        channel = new JChannel(JGroupsCluster.class.getResourceAsStream("/jgroups.xml"));
        channel.setDiscardOwnMessages(true)
        channel.connect("photon-cluster");
    }

    void start(EventDistribution distribution) {
        channel.setReceiver(new Receiver() {
            @Override
            void receive(Message msg) {
                Event ev = codecs.decode(msg.buffer(), codecs.getAvailableCodecs()[0], Event)
                distribution.distributeOnly(ev)
            }
        })
    }

    @Override
    void dispatch(Event event) {
        def result = codecs.encode(event, codecs.getAvailableCodecs())
        channel.send(null, result.payload)
    }

}
