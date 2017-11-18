package io.muoncore.photonlite

import io.muoncore.Muon
import io.muoncore.api.MuonFuture
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
import spock.lang.Unroll
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
        store.deleteStream("my-stream")

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

    @Unroll
    def "can persist and replay #itemcount events - hot"() {
        given:
        def data = []
        def cl = new DefaultEventClient(muon)

        subscribe(cl, data, EventReplayMode.LIVE_ONLY)

        when:
        itemcount.times {
            cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            )
        }

        then:
        new PollingConditions().eventually {
            data.size() == itemcount
        }

        cleanup:
        store.deleteStream("my-stream")

        where:
        itemcount << itemCounts
    }

    @Unroll
    def "can persist and replay #itemcount events - hot-cold"() {
        given:
        store.clear()

        def data = []
        def cl = new DefaultEventClient(muon)

        def coldemitted = []
        when:
        (0..itemcount).each {
            coldemitted << cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            ).orderId
        }

        println "Emitted COLD data, subscribing then starting HOT set"

        sleep(200)
        subscribe(cl, data, EventReplayMode.REPLAY_THEN_LIVE)
        sleep(200)

        def emitted = []

        def then = System.currentTimeMillis()

        println "Started subscribing, emitting HOT data $itemcount"
        (0..itemcount).each {
            print "Emitting ${it} of $itemcount"
            emitted << cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            ).orderId
            println " ... done ${emitted.size()}"
        }

        def now = System.currentTimeMillis()
        println "Completed emitting data, took ${now - then}ms (around ${(now - then) / itemcount}ms per event)"

        then:
        new PollingConditions(timeout: 120).eventually {
            data.collect{it.orderId}.size() == emitted.size() + coldemitted.size()
        }

        cleanup:
        store.deleteStream("my-stream")

        where:
        itemcount << itemCounts
    }

    @Unroll
    def "can persist and replay #itemcount events - cold"() {
        given:
        store.clear()
        def data = []
        def emitted = []
        def closed = false
        def cl = new DefaultEventClient(muon)

        def id = UUID.randomUUID().toString()
        when:
        def then = System.currentTimeMillis()
        (0..itemcount).each {
            emitted << cl.event(ClientEvent.ofType("ProductAdded")
                    .stream("my-stream")
                    .payload([
                    message: "hello"
            ]).build()
            ).orderId
        }
        def now = System.currentTimeMillis()
        println "Persisted ${itemcount} events for COLD replay in ${now - then}ms (thats about ${(now - then) / itemcount}ms per event)"
        sleep(200)
        then = System.currentTimeMillis()
        def start = 0
        cl.replay("my-stream", EventReplayMode.REPLAY_ONLY, [:], new Subscriber<Event>() {
            @Override
            void onSubscribe(Subscription subscription) {
                subscription.request(Integer.MAX_VALUE)
            }

            @Override
            void onNext(Event event) {
                if (!start) start = System.currentTimeMillis()
                data << event
                println "DATA!"
            }

            @Override
            void onError(Throwable throwable) {
                throwable.printStackTrace()
            }

            @Override
            void onComplete() {
                now = System.currentTimeMillis()
                println "COLD Replay $id completed in ${now - then}ms (thats about ${(now - then) / data.size()}ms per event)"
                println "Latency from subscribe to first data was ${start - then}ms"
                closed = true
            }
        })

        then:
        new PollingConditions(timeout: 200).eventually {
            data.size() == emitted.size()
            closed == true
        }

        cleanup:
        store.deleteStream("my-stream")

        where:
        itemcount << coldItemCounts
    }

    List getItemCounts() {
        [12, 120, 1200]
    }

    List getColdItemCounts() {
        [12, 120, 1200]
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

    def "active hot subscriptions and emit events"() {
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
        store.clear()
        def data = []
        def cl = new DefaultEventClient(muon)
        def rpc = new RpcClient(muon)

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
        rpc.request("rpc://photonlite/stream-names").get().getPayload(Map) == [
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