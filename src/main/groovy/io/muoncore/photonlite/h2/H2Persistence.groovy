package io.muoncore.photonlite.h2

import io.muoncore.codec.Codecs
import io.muoncore.codec.json.GsonCodec
import io.muoncore.photonlite.Persistence
import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import reactor.rx.Streams

import javax.transaction.Transactional

class H2Persistence implements Persistence {

    @Autowired
    EventRepo repo

    @Autowired
    Codecs codecs

    @Override
    @Transactional
    void persist(EventWrapper event) {

        def encode = codecs.encode(event.event, new GsonCodec().contentType)

        def record = repo.save(new EventRecord(
                stream: event.event.streamName,
                payload: encode.payload,
                encoding: encode.contentType,
                eventTime: System.currentTimeMillis()
        ))
        event.event.orderId = record.orderId
        event.event.eventTime = record.eventTime
        event.persisted(record.orderId, record.eventTime)
    }

    @Override
    @Transactional
    void deleteStream(String name) {
        repo.deleteByStream(name)
    }

    @Override
    List streamNames() {
        return repo.findAllDistinctStream()
    }

    @Override
    Publisher<Event> replayEvent(String name, String type, long from) {
        def ret = repo.findAllByStreamAndOrderIdGreaterThan(name, from - 1)
        Streams.from(ret).map {
            def ev = codecs.decode(it.payload, it.encoding, Event)
            ev.orderId = it.orderId
            ev.eventTime = it.eventTime
            ev
        }
    }
}
