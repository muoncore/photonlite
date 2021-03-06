package io.muoncore.photonlite

import io.muoncore.Muon
import io.muoncore.photonlite.mongo.MongoPersistence
import io.muoncore.protocol.event.server.EventServerProtocolStack
import io.muoncore.protocol.reactivestream.messages.ReactiveStreamSubscriptionRequest
import io.muoncore.protocol.reactivestream.server.PublisherLookup
import io.muoncore.protocol.reactivestream.server.ReactiveStreamServer
import io.muoncore.protocol.rpc.server.HandlerPredicates
import io.muoncore.protocol.rpc.server.RequestWrapper
import io.muoncore.protocol.rpc.server.RpcServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static io.muoncore.protocol.rpc.server.HandlerPredicates.path

@Component
class EventStore {

    @Autowired
    Persistence persistence

    @Autowired
    EventDistribution distribution

    public static Logger log = LoggerFactory.getLogger(EventStore)

    void clear() {
        if (persistence instanceof InMemPersistence || persistence instanceof MongoPersistence) {
            persistence.clear()
        }
    }

    void deleteStream(String name) {
        persistence.deleteStream(name)
    }

    EventStore(Muon muon, ReactiveStreamServer reactiveStreamServer, RpcServer rpcServer) {

        rpcServer.handleRequest(path("/stats")) { RequestWrapper request ->
            request.ok([
                    persistence: persistence.stats,
                    streams: distribution.stats,
                    cluster: distribution.clusterMessaging.stats
            ])
        }

        rpcServer.handleRequest(path("/stream-names")) { RequestWrapper request ->
            def names = persistence.streamNames().sort().collect {
                ["stream-name": it]
            }
            request.ok([streams: names])
        }

        rpcServer.handleRequest(path("/stream/delete")) { RequestWrapper request ->
            def stream = request.request.getPayload(Map).name
            deleteStream(stream)
            request.ok("Deleted stream ${stream}")
        }

        reactiveStreamServer.publishGeneratedSource("/stream", PublisherLookup.PublisherType.HOT_COLD) { ReactiveStreamSubscriptionRequest request ->
            long from = Long.valueOf(request.args["from"] ?: 0 )

            def stream = request.args["stream-name"]
            def subName = request.args["sub-name"]
            def streamType = request.args["stream-type"]
            if (!streamType) streamType = "hot-cold"

            distribution.subscribeToLive(stream, streamType, from, subName)
        }

        muon.protocolStacks.registerServerProtocol(new EventServerProtocolStack({ event ->
            log.debug "Event received " + event.event
            distribution.distribute(event)
        }, muon.getCodecs(), muon.getDiscovery()));
    }
}
