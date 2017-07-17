package io.muoncore.photonlite.mongo

import org.springframework.data.mongodb.repository.MongoRepository

interface MongoEventRepo extends MongoRepository<EventRecord, Long> {

//    Page<EventRecord> findByStream(String stream, Pageable pageable, Sort sort)
    List<EventRecord> findByStreamNameAndOrderIdGreaterThanEqual(String stream, long orderId)
}
