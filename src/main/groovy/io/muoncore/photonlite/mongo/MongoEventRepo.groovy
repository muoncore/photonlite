package io.muoncore.photonlite.mongo

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.MongoRepository

@Profile("mongo")
interface MongoEventRepo extends MongoRepository<EventRecord, Long> {

//    Page<EventRecord> findByStream(String stream, Pageable pageable, Sort sort)
    List<EventRecord> findByStreamNameAndOrderIdGreaterThanEqual(String stream, long orderId)
}
