package io.muoncore.photonlite.gcpdatastore

import com.google.cloud.datastore.Blob
import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.KeyFactory
import com.google.cloud.datastore.StructuredQuery
import groovy.util.logging.Slf4j
import io.muoncore.codec.DelegatingCodecs
import io.muoncore.codec.MuonCodec
import io.muoncore.codec.json.GsonCodec
import io.muoncore.photonlite.Persistence
import io.muoncore.protocol.event.Event
import io.muoncore.protocol.event.server.EventWrapper
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import java.util.concurrent.atomic.AtomicInteger

import static com.google.cloud.datastore.Query.*

@Slf4j
class GCPCloudDatastorePersistence implements Persistence {

    private Datastore store
    private MuonCodec codec = new GsonCodec()
    private KeyFactory keyFactory
    private AtomicInteger inc = new AtomicInteger(1)

    public GCPCloudDatastorePersistence() {
        store = DatastoreOptions.defaultInstance.service
        keyFactory = store.newKeyFactory()
        //TODO, add tenancy ...
                .setKind("Event")
    }

    @Override
    void persist(EventWrapper event) {

        log.info("STARTING PERSISTENCE")
        try {
            String kind = "Event"

            def taskKey = keyFactory.newKey(System.nanoTime() + inc.incrementAndGet())

            long now = System.currentTimeMillis()

            def task = Entity.newBuilder()

            set(task, "eventType", event.event.eventType)
            set(task, "service", event.event.service)
            set(task, "streamName", event.event.streamName)
            set(task, "causedById", event.event.causedById)
            set(task, "causedByRelation", event.event.causedByRelation)
            set(task, "schema", event.event.schema)
            set(task, "eventTime", now)
            set(task, "orderId", taskKey.id)
            set(task, "payload", Blob.copyFrom(codec.encode(event.event.payload)))

            def ret = store.put(task.setKey(taskKey).build())

            def retKey = ret.key
            event.event.orderId = retKey.id
            event.event.eventTime = now

            event.persisted(retKey.id, now)
        } catch (e) {
            event.failed(e.message)
            log.error("Failed to persiste", e)
        }
    }

    private static set(FullEntity.Builder e, String name, def value) {
        if (value != null) {
            e.set(name, value)
        }
    }

    @Override
    void deleteStream(String name) {
        log.info("Deletino stream ${name}, expensive operation")

        def q = newKeyQueryBuilder()
                .setFilter(StructuredQuery.PropertyFilter.eq("streamName", name))
                .setKind("Event")
//                    .setNamespace("awesome")
                .build()

        def ret = store.run(q)

        def del = []

        while(ret.hasNext()) {
            del << ret.next()
            if (del.size() >= 450) {
                store.delete(del as Key[])
                log.info("Delete ${del.size()} keys")
                del.clear()
            }
        }

        store.delete(del as Key[])
        log.info("Deleted ${del.size()} keys, end of stream")
    }

    @Override
    List streamNames() {
        return []
    }

    @Override
    Publisher<Event> replayEvent(String name, String type, long from) {

        try {

            StructuredQuery.Filter filter

            if (from > 0) {
                Key queryKey = keyFactory.newKey(from)

                filter = StructuredQuery.CompositeFilter.and(
                        StructuredQuery.PropertyFilter.eq("streamName", name),
                        StructuredQuery.PropertyFilter.ge("orderId", queryKey)
                )
            } else {
                filter = StructuredQuery.PropertyFilter.eq("streamName", name)

            }
            def q = newEntityQueryBuilder()
                    .setKind("Event")
//                    .setNamespace("awesome")
                    .setFilter(filter).build()

            def ret = store.run(q)

            Iterable<Entity> iterable = { ret }

            def flow = Flowable.fromIterable(iterable).map {

                log.info("LOADING ${it}")

                Map payload = codec.decode(it.getBlob("payload").toByteArray(), Map)

                new Event(
                        "${it.key.id}".toString(),
                        getOpt(it, "eventType"),
                        getOpt(it, "streamName"),
                        getOpt(it, "schema"),
                        getOpt(it, "causedById"),
                        getOpt(it, "causedByRelation"),
                        getOpt(it, "service"),
                        it.key.id,
                        it.getLong("eventTime"),
                        payload, new DelegatingCodecs().withCodec(codec)
                )
            }

            flow
        } catch (e) {
            e.printStackTrace()
            throw e
        }
    }

    static String getOpt(Entity e, def name) {
        if (e.getNames().contains(name)) {
            return e.getString(name)
        }
        null
    }

    @Override
    Map getStats() {
        return null
    }
}

