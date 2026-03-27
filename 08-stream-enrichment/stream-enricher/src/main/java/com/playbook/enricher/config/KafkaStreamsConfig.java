package com.playbook.enricher.config;

import com.playbook.enricher.model.ClickEvent;
import com.playbook.enricher.model.EnrichedClick;
import com.playbook.enricher.model.UserProfile;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

@Configuration
public class KafkaStreamsConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamsConfig.class);

    @Bean
    public KStream<String, EnrichedClick> enrichmentStream(StreamsBuilder builder) {
        JsonSerde<ClickEvent> clickSerde = new JsonSerde<>(ClickEvent.class);
        clickSerde.configure(java.util.Map.of(
                "spring.json.trusted.packages", "com.playbook.*"
        ), false);

        JsonSerde<UserProfile> profileSerde = new JsonSerde<>(UserProfile.class);
        profileSerde.configure(java.util.Map.of(
                "spring.json.trusted.packages", "com.playbook.*"
        ), false);

        JsonSerde<EnrichedClick> enrichedSerde = new JsonSerde<>(EnrichedClick.class);
        enrichedSerde.configure(java.util.Map.of(
                "spring.json.trusted.packages", "com.playbook.*"
        ), false);

        KStream<String, ClickEvent> clicks = builder.stream("clicks",
                Consumed.with(Serdes.String(), clickSerde));

        KTable<String, UserProfile> profiles = builder.table("user-profiles",
                Consumed.with(Serdes.String(), profileSerde));

        KStream<String, EnrichedClick> enriched = clicks.join(profiles,
                (click, profile) -> {
                    log.info("Joining click {} with profile {} ({})",
                            click.clickId(), profile.userId(), profile.name());
                    return new EnrichedClick(
                            click.clickId(),
                            click.userId(),
                            profile.name(),
                            profile.tier(),
                            click.page(),
                            click.action(),
                            click.timestamp()
                    );
                });

        enriched.to("enriched-clicks", Produced.with(Serdes.String(), enrichedSerde));

        return enriched;
    }
}
