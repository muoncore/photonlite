package io.muoncore.photonlite

import groovy.util.logging.Slf4j
import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import org.reactivestreams.Publisher
import org.springframework.stereotype.Component
import reactor.rx.Streams
import reactor.rx.broadcast.Broadcaster

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong

@Slf4j
class InMemPersistence implements Persistence {

    List<Event> history = new ArrayList<>();
    AtomicLong orderid = new AtomicLong(1)

    void clear() {
        history.clear()
    }

    @Override
    void persist(EventWrapper event) {
        try {
            synchronized (history) {
                def orderId = orderid.addAndGet(1)
                Event ev = event.event
                ev.orderId = orderId
                ev.eventTime = System.currentTimeMillis()
                log.info("persisted $ev")
                history.add(ev);
                event.persisted(ev.orderId, ev.eventTime);
            }
        } catch (Exception ex) {
            event.failed(ex.getMessage());
        }
    }

    @Override
    void deleteStream(String name) {
        history.removeAll {
            it.streamName == name
        }
    }

    @Override
    List streamNames() {
        def ret = history.collect { it.streamName } as Set
        ret as List
    }

    @Override
    Publisher replayEvent(String stream, String streamType, long from) {
        log.info "Subscribing to $stream"

        def b = Broadcaster.<Event> create()

        BlockingQueue q = new LinkedBlockingQueue()

        b.map {
            if (it instanceof EventWrapper) {
                return it.event
            }
            it
        }.filter { it.streamName == stream }.consume {
            q.add(it)
        }

        return Streams.from(history.findAll {
            if (it.orderId >= from) {
                return matches(it.streamName, stream)
            }
            false
        })
    }

    def matches(name, pattern) {
        pattern = pattern.replaceAll("\\*\\*", ".\\*")
        name ==~ pattern
    }
}
