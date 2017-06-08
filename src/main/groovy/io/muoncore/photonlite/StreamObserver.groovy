package io.muoncore.photonlite

import groovy.util.logging.Slf4j
import io.muoncore.protocol.event.Event
import org.reactivestreams.Processor
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import java.util.concurrent.ConcurrentLinkedQueue

@Slf4j
class StreamObserver implements Processor<Event, Event> {

    private Object monitor

    private ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue()
    //TODO, control the cold replay so it only starts when the sub is here, plays data as fast as the sub accepts. there's no reason we should buffer here
    private ConcurrentLinkedQueue<Event> coldBuffer = new ConcurrentLinkedQueue()

    Subscriber sub

    private String streamName
    boolean cancelled = false
    long requested = 0
    long orderId = 0
    boolean coldPlayed = true
    boolean coldOnly = false

    void dispatch() {
        if (sub && coldPlayed && coldBuffer.size() == 0) {
            def item = queue.poll()
            while (item && requested > 0) {
                log.debug "Dispatching live event ${item.orderId} to observer"
                requested--
                sub.onNext(item)
                item = queue.poll()
            }
        } else if (sub) {

            while (coldBuffer.peek() && requested > 0) {
                def item = coldBuffer.poll()
                log.debug "Dispatching cold event ${item.orderId} to observer"
                requested--
                sub.onNext(item)
            }
            if (coldOnly) complete()
        }
    }

    void complete() {
        if (sub) sub.onComplete()
    }

    void accept(Event event) {
        log.debug "Testing event ${event.orderId}"
        if (!coldOnly && streamName == event.streamName &&event.orderId >= orderId) {
            queue.offer(event)
        }
    }

    @Override
    void subscribe(Subscriber<? super Event> subscriber) {
        sub = subscriber
        subscriber.onSubscribe(new Subscription() {
            @Override
            void request(long l) {

                if (coldOnly && coldPlayed && coldBuffer.size() == 0) {
                    subscriber.onComplete()
                    return
                }

                requested += l
                synchronized(monitor) {
                    monitor.notifyAll()
                }
            }

            @Override
            void cancel() {
                cancelled = true
            }
        })
    }

    @Override
    void onSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE)
    }

    @Override
    void onNext(Event event) {
        coldBuffer.offer(event)
    }

    @Override
    void onError(Throwable throwable) {
        throwable.printStackTrace()
        coldPlayed = true
    }

    @Override
    void onComplete() {
        coldPlayed = true
        if (coldOnly) complete()
    }
}
