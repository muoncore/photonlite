package io.muoncore.photonlite

import groovy.util.logging.Slf4j
import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import org.reactivestreams.Processor
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
@Slf4j
class EventDistribution {

    @Autowired
    Persistence persistence
    @Autowired
    ClusterMessaging clusterMessaging

    List<PublishSubject> observers = Collections.synchronizedList([])

    @PostConstruct
    void startup() {
        clusterMessaging.start(this)
    }

    Publisher subscribeToLive(String streamName, String type, long orderId, String subName) {
        def id = UUID.randomUUID().toString()

        log.info("Creating new StreamObserver stream={} type={} from={} subname={}", streamName, type, orderId, subName)
        switch(type) {
            case "cold":
                return persistence.replayEvent(streamName, type, orderId)
            case "hot":
                PublishSubject<Event> sub = PublishSubject.create()

                observers << sub

                return new ResourceManagingSubscriber(delegate: sub.toFlowable(BackpressureStrategy.LATEST).filter {
                    def ret = it.streamName == streamName && it.orderId >= orderId
                    log.info("HOT: $subName, {}", it)
                    return ret
                }, finished: {
                    log.debug("HOT subscription is now completed {}", id)
                    observers.remove(sub)
                })
            default:

                PublishSubject<Event> sub = PublishSubject.create()

                observers << sub

                return new ResourceManagingSubscriber(delegate: Flowable.concat(persistence.replayEvent(streamName, type, orderId), sub.toFlowable(BackpressureStrategy.LATEST).filter {
                    def ret = it.streamName == streamName && it.orderId >= orderId
                    return ret
                }), finished: {
                    log.debug("Hot-Cold sub is completed {}")
                    observers.remove(sub)
                })
        }
    }

    void distributeOnly(Event event) {
        log.debug("Dispatching to {} subs: {}", observers.size(), event)
        observers*.onNext(event)
    }

    void distribute(EventWrapper wrapper) {
        Event event = wrapper.event
        persistence.persist(wrapper)
        clusterMessaging.dispatch(wrapper.event)
        distributeOnly(event)
    }
}

@Slf4j
class ResourceManagingSubscriber implements Processor {

    Publisher delegate
    Runnable finished
    private Subscriber sub

    @Override
    void subscribe(Subscriber subscriber) {
        sub = subscriber
        delegate.subscribe(this)
    }

    @Override
    void onSubscribe(Subscription subscription) {
        sub.onSubscribe(new Subscription() {
            @Override
            void request(long l) {
                subscription.request(l);
            }

            @Override
            void cancel() {
                subscription.cancel()
                finished.run()
            }
        })
    }

    @Override
    void onNext(Object o) {
        sub.onNext(o)
    }

    @Override
    void onError(Throwable throwable) {
        sub.onError(throwable)
        finished.run()
    }

    @Override
    void onComplete() {
        sub.onComplete()
        finished.run()
    }
}