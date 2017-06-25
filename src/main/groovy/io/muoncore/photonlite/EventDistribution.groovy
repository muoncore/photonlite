package io.muoncore.photonlite

import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Component
@groovy.util.logging.Slf4j
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
        def ob

        log.info("Creating new StreamObserver stream={} type={} from={} subname={}", streamName, type, orderId, subName)
        switch(type) {
            case "cold":
                return persistence.replayEvent(streamName, type, orderId)
            case "hot":
                log.info("Creating hot subscription")
                PublishSubject<Event> sub = PublishSubject.create()

                observers << sub

                return sub.toFlowable(BackpressureStrategy.LATEST).filter {
                    def ret = it.streamName == streamName && it.orderId >= orderId
                    log.info("HOT: $subName, {}", it)
                    return ret
                }
            default:

                PublishSubject<Event> sub = PublishSubject.create()

                observers << sub

                return Flowable.concat(persistence.replayEvent(streamName, type, orderId), sub.toFlowable(BackpressureStrategy.LATEST).filter {
                    def ret = it.streamName == streamName && it.orderId >= orderId
                    log.info("COLDHOT: $subName, {} {}", ret, it)
                    return ret
                }).doOnCancel {
                    log.info("Sub {} was CANCELLED, ${it}", subName)
                }.doOnComplete {
                    log.info("Sub {} was COMPLETED", subName)
                }
        }
    }

    void distributeOnly(Event event) {
        log.info("Dispatching to {} subs: {}", observers.size(), event)
        observers*.onNext(event)
    }

    void distribute(EventWrapper wrapper) {
        Event event = wrapper.event
        persistence.persist(wrapper)
        clusterMessaging.dispatch(wrapper.event)
        distributeOnly(event)
    }
}
