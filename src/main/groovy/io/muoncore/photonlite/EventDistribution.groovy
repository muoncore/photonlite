package io.muoncore.photonlite

import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Component
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

        switch(type) {
            case "cold":
                ob = new StreamObserver(monitor: monitor, streamName: streamName, orderId: orderId, coldPlayed: false, coldOnly:true)
                persistence.replayEvent(streamName, type, orderId).subscribe(ob)
                synchronized (monitor) {
                    observers << ob
                }
                break
            case "hot":
                ob = new StreamObserver(monitor: monitor, streamName: streamName, orderId: orderId, coldPlayed: true)
                synchronized (monitor) {
                    observers << ob
                }
                break
            default:
                ob = new StreamObserver(monitor: monitor, streamName: streamName, orderId: orderId, coldPlayed: false)
                persistence.replayEvent(streamName, type, orderId).subscribe(ob)
                synchronized (monitor) {
                    observers << ob
                }
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
