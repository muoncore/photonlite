package io.muoncore.photonlite.mongo

import com.google.gson.annotations.SerializedName
import io.muoncore.codec.Codecs
import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@AllArgsConstructor
@NoArgsConstructor
@Data
@Document
@CompoundIndexes([
    @CompoundIndex(name = "replay_from", def = "{'id' : 1, 'streamName': 1}")
])
class EventRecord {

    @Field("entity_id")
    String id
    
    @Indexed
    @Id
    Long orderId
    Long eventTime

    @Indexed
    String streamName

    String eventType

    String schema

    String causedById

    String causedByRelation

    String service
    Map payload
}
