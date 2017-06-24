package io.muoncore.photonlite

import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import lombok.extern.slf4j.Slf4j
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Component
@groovy.util.logging.Slf4j
class EventDistribution {

    private final monitor = new Object()

    @Autowired
    Persistence persistence
    @Autowired
    ClusterMessaging clusterMessaging

    private Executor exec = Executors.newSingleThreadExecutor()

    List<StreamObserver> observers = Collections.synchronizedList([])

    @PostConstruct
    void startup() {
        clusterMessaging.start(this)
        exec.execute({
            while(true) {

                synchronized (monitor) {
                    observers.removeAll{ it.cancelled }
                    observers*.dispatch()
                }

                try {
                    synchronized (monitor) {
                        monitor.wait(2000)
                    }
                } catch (Exception e) {}
            }
        })
    }

    Publisher subscribeToLive(String streamName, String type, long orderId) {
        def ob

        log.info("Creating new StreamObserver stream={} type={} from={}", streamName, type, orderId)
        switch(type) {
            case "cold":
                return persistence.replayEvent(streamName, type, orderId)
                break
            case "hot":
                ob = new StreamObserver(monitor: monitor, streamName: streamName, orderId: orderId, coldPlayed: true)
                synchronized (monitor) {
                    observers << ob
                }
                break
            default:
                ob = new StreamObserver(monitor: monitor, streamName: streamName, orderId: orderId, coldPlayed: false)
                synchronized (monitor) {
                    observers << ob
                }
                persistence.replayEvent(streamName, type, orderId).subscribe(ob)
        }
        ob
    }

    void distributeOnly(Event event) {
        synchronized (monitor) {
            observers*.accept(event)
            monitor.notifyAll()
        }
    }

    void distribute(EventWrapper wrapper) {
        Event event = wrapper.event
        persistence.persist(wrapper)
        clusterMessaging.dispatch(wrapper.event)
        distributeOnly(event)
    }
}
