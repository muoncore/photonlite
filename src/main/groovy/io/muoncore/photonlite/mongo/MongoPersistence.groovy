package io.muoncore.photonlite.mongo

import groovy.util.logging.Slf4j
import io.muoncore.codec.Codecs
import io.muoncore.codec.json.JsonOnlyCodecs
import io.muoncore.photonlite.Persistence
import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import org.reactivestreams.Publisher
import org.springframework.beans.BeanUtils
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import reactor.rx.Streams

@Slf4j
class MongoPersistence implements Persistence {

    MongoEventRepo repo
    Codecs codecs = new JsonOnlyCodecs()
    MongoTemplate template

    long getNextId() {
        Query query = new Query(Criteria.where("name").is("event-sequence"));
        Update update = new Update().inc("sequence", 1);
        Counter counter = template.findAndModify(query, update, Counter)
        return counter.sequence
    }

    void prepareIndex() {
        Query query = new Query(Criteria.where("name").is("event-sequence"));
        Update update = new Update().inc("sequence", 1);
        template.upsert(query, update, Counter)
    }

    @Override
    void persist(EventWrapper event) {

        def ev = new EventRecord(
                orderId: nextId,
                payload: event.event.getPayload(Map),
                eventTime: System.currentTimeMillis()
        )

        BeanUtils.copyProperties(event.event, ev, "orderId", "eventTime", "payload")

        def record = repo.save(ev)
        event.event.orderId = record.orderId
        event.event.eventTime = record.eventTime
        event.persisted(record.orderId, record.eventTime)
    }

    @Override
    void deleteStream(String name) {
        Query query = new Query(Criteria.where("streamName").is(name))

        template.remove(query, EventRecord)
    }

    @Override
    List streamNames() {
        return template.getCollection(template.getCollectionName(EventRecord)).distinct("streamName")
    }

    void clear() {
        log.warn("Clearing entire event store")
        Query query = new Query()
        template.remove(query, EventRecord)
    }

    @Override
    Publisher<Event> replayEvent(String name, String type, long from) {
        Streams.from(repo.findByStreamNameAndOrderIdGreaterThanEqual(name, from)).map {
            new Event(it.eventType, it.streamName, it.schema, it.causedById, it.causedByRelation, it.service, it.orderId, it.eventTime, it.payload, codecs)
        }
    }

    @Override
    Map getStats() {

        Query query = new Query()

        [
                "streams": streamNames().size(),
                "events": template.count(query, EventRecord)]
    }
}
