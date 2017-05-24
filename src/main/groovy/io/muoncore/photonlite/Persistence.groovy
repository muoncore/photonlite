package io.muoncore.photonlite

import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import org.reactivestreams.Publisher

interface Persistence {

    void persist(EventWrapper event)
    void deleteStream(String name)
    List streamNames()
    Publisher<Event> replayEvent(String name, String type, long from)
}
