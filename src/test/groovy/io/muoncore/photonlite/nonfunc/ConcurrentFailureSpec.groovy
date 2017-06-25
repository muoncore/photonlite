package io.muoncore.photonlite.nonfunc

import io.muoncore.Muon
import io.muoncore.api.MuonFuture
import io.muoncore.photonlite.EventStore
import io.muoncore.photonlite.EventStoreApplication
import io.muoncore.photonlite.MuonTestConfig
import io.muoncore.protocol.event.ClientEvent
import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.client.DefaultEventClient
import io.muoncore.protocol.event.client.EventReplayControl
import io.muoncore.protocol.event.client.EventReplayMode
import io.muoncore.protocol.rpc.client.RpcClient
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@ActiveProfiles("test")
@SpringBootTest(classes = [MuonTestConfig, EventStoreApplication])
class ConcurrentFailureSpec extends Specification {

    @Autowired
    EventStore store

    @Autowired
    Muon muon

    def "can persist and replay events cold from"() {
        given:
        def data = []
        def data2 = []
        def cl = new DefaultEventClient(muon)

        def items = []
        when:
        100.times {
            items << cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            ).orderId
        }

        subscribe(cl, data, EventReplayMode.REPLAY_ONLY, ["from": 0])
        subscribe(cl, data2, EventReplayMode.REPLAY_ONLY, ["from": 0])
        subscribe(cl, data2, EventReplayMode.REPLAY_ONLY, ["from": 0])
        subscribe(cl, data2, EventReplayMode.REPLAY_ONLY, ["from": 0])
        subscribe(cl, data2, EventReplayMode.REPLAY_ONLY, ["from": 0])
        subscribe(cl, data2, EventReplayMode.REPLAY_ONLY, ["from": 0])
        sleep(100)
        5.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-faked-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        and: "Cold replay an existing stream"
        def replayed = []
        subscribe(cl, replayed, EventReplayMode.REPLAY_ONLY, [:], "my-faked-stream")

        then:
        new PollingConditions().eventually {
            data.size() == 100
            data2.size() == 500
            replayed.size() == 5
        }

        cleanup:
        store.deleteStream("my-stream")
        store.deleteStream("my-faked-stream")
    }

    private MuonFuture<EventReplayControl> subscribe(DefaultEventClient cl, data, type, args = [:], streamName = "my-stream") {
        cl.replay(streamName, type, args, new Subscriber<Event>() {
            @Override
            void onSubscribe(Subscription subscription) {
                subscription.request(Integer.MAX_VALUE)
            }

            @Override
            void onNext(Event event) {
                data << event
            }

            @Override
            void onError(Throwable throwable) {
                throwable.printStackTrace()
            }

            @Override
            void onComplete() {
                println "Completed"
            }
        })
    }
}