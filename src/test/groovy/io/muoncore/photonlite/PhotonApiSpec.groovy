package io.muoncore.photonlite

import io.muoncore.Muon
import io.muoncore.api.MuonFuture
import io.muoncore.protocol.event.ClientEvent
import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.client.DefaultEventClient
import io.muoncore.protocol.event.client.EventReplayControl
import io.muoncore.protocol.event.client.EventReplayMode
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@ActiveProfiles("test")
@SpringBootTest(classes = [MuonTestConfig, EventStoreApplication])
abstract class PhotonApiSpec extends Specification {

    @Autowired
    EventStore store

    @Autowired
    Muon muon

    def "on cold replay of a non existing stream, immediately sends closed"() {
        given:
        def data = []
        def closed = false
        def cl = new DefaultEventClient(muon)

        when:
        cl.replay("my-stream", EventReplayMode.REPLAY_ONLY, [:], new Subscriber<Event>() {
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
                closed = true
            }
        })

        then:
        new PollingConditions().eventually {
            data.size() == 0
            closed == true
        }

        cleanup:
        store.deleteStream("my-stream")
    }

    def "can persist and replay events - hot"() {
        given:
        def data = []
        def cl = new DefaultEventClient(muon)

        subscribe(cl, data, EventReplayMode.LIVE_ONLY)

        when:
        5.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        then:
        new PollingConditions().eventually {
            data.size() == 5
        }

        cleanup:
        store.deleteStream("my-stream")
    }

    def "can persist and replay events - hot-cold"() {
        given:
        def data = []
        def cl = new DefaultEventClient(muon)

        when:
        5.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        sleep(200)
        subscribe(cl, data, EventReplayMode.REPLAY_THEN_LIVE)
        sleep(200)
        5.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        then:
        new PollingConditions().eventually {
            data.size() == 10
        }

        cleanup:
        store.deleteStream("my-stream")
    }

    def "can persist and replay events - cold"() {
        given:
        def data = []
        def closed = false
        def cl = new DefaultEventClient(muon)

        when:
        5.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        sleep(200)
        cl.replay("my-stream", EventReplayMode.REPLAY_ONLY, [:], new Subscriber<Event>() {
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
                closed = true
            }
        })
        sleep(50)
        12.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        then:
        new PollingConditions().eventually {
            data.size() == 5
            closed == true
        }

        cleanup:
        store.deleteStream("my-stream")
    }

    def "can persist and replay events cold-from"() {
        given:
        def data = []
        def cl = new DefaultEventClient(muon)

        def items = []
        when:
        5.times {
            items << cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            ).orderId
        }

        sleep(200)
        subscribe(cl, data, EventReplayMode.REPLAY_ONLY, ["from": items[3]])
        sleep(50)
        5.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        then:
        new PollingConditions().eventually {
            data.size() == 2
        }

        cleanup:
        store.deleteStream("my-stream")
    }

    def "can persist and replay events hot-cold from"() {
        given:
        def data = []
        def cl = new DefaultEventClient(muon)

        def items = []
        when:
        5.times {
            items << cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            ).orderId
        }

        sleep(100)
        subscribe(cl, data, EventReplayMode.REPLAY_THEN_LIVE, ["from": items[3]])
        sleep(100)
        5.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-faked-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        5.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        then:
        new PollingConditions().eventually {
            data.size() == 7
        }

        cleanup:
        store.deleteStream("my-stream")
        store.deleteStream("my-faked-stream")
    }

    def "returns stream-names"() {
        given:
        def data = []
        def cl = new DefaultEventClient(muon)

        when:
        cl.event(ClientEvent.ofType("ProductAdded")
                .stream("my-stream")
                .payload([
                message: "hello"
        ]).build())

        cl.event(ClientEvent.ofType("ProductAdded")
                .stream("my-stream1")
                .payload([
                message: "hello"
        ]).build())

        cl.event(ClientEvent.ofType("ProductAdded")
                .stream("my-stream2")
                .payload([
                message: "hello"
        ]).build())

        then:
        muon.request("rpc://photonlite/stream-names").get().getPayload(Map) == [
                "streams":[
                ["stream-name": "my-stream"],
                ["stream-name": "my-stream1"],
                ["stream-name": "my-stream2"]]
        ]

        cleanup:
        store.deleteStream("my-stream")
        store.deleteStream("my-stream1")
        store.deleteStream("my-stream2")
    }

    private MuonFuture<EventReplayControl> subscribe(DefaultEventClient cl, data, type, args = [:]) {
        cl.replay("my-stream", type, args, new Subscriber<Event>() {
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


/*

can have event emit


event replay
  cold
  hot
  hot-cold
  from

stream-names

 */