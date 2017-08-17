package io.muoncore.photonlite.mongo

import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import io.muoncore.codec.Codecs
import io.muoncore.codec.json.JsonOnlyCodecs
import io.muoncore.photonlite.Persistence
import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.annotations.NonNull
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.springframework.beans.BeanUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

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
        return new EventPub(codecs: codecs, stream: name, orderId: from, repo: repo).start()
    }

    @Override
    Map getStats() {

        Query query = new Query()

        [
                "streams": streamNames().size(),
                "events": template.count(query, EventRecord)]
    }
}

@Slf4j
class EventPub implements Publisher {

    Codecs codecs

    String stream
    int orderId

    MongoEventRepo repo
    int page = 0
    int pageSize = 150

    int requestLeft = 0

    def items = new LinkedBlockingQueue()

    Subscriber sub

    boolean running = true;

    CountDownLatch latch = new CountDownLatch(1)

    EventPub start() {
        Thread.start {
            latch.await()
            latch = new CountDownLatch(1)
            while(running) {
                if (items.size() == 0) {
                    tryRefill()
                }
                if (items.size() == 0) {
                    log.info("Stream $stream replay is completed")
                    running = false
                    sub.onComplete()
                } else if (requestLeft == 0) {
                        log.debug("Client has not requested more data, blocking until client needs more")
                        latch.await()
                        latch = new CountDownLatch(1)
                } else {
                    
                    EventRecord rec = items.poll()
                    
                    sub.onNext(new Event(rec.eventType, rec.streamName, rec.schema, rec.causedById, rec.causedByRelation, rec.service, rec.orderId, rec.eventTime, rec.payload, codecs))
                    requestLeft--
                }
            }
        }
        this
    }

    @Override
    void subscribe(Subscriber subscriber) {
        sub = subscriber
        subscriber.onSubscribe(new Subscription() {
            @Override
            void request(long l) {
                requestLeft += l
                latch.countDown()
            }

            @Override
            void cancel() {
                running = false;
            }
        })
    }

    void tryRefill() {
        items.addAll(repo.findByStreamNameAndOrderIdGreaterThanEqual(stream, orderId, new PageRequest(page++, pageSize)).content)
    }
}
