package guru.learningjournal.kafka.examples;

import guru.learningjournal.kafka.examples.serde.AppSerdes;
import guru.learningjournal.kafka.examples.types.Notification;
import guru.learningjournal.kafka.examples.types.PosInvoice;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

public class RewardsApp {
    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, AppConfigs.applicationID);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfigs.bootstrapServers);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG,0);

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, Notification> source = builder.stream(AppConfigs.posTopicName,
            Consumed.with(AppSerdes.String(), AppSerdes.PosInvoice())
        ).filter((key, value) -> value.getCustomerType().equalsIgnoreCase(AppConfigs.CUSTOMER_TYPE_PRIME))
                .map((key, invoice) -> new KeyValue<>(
                        invoice.getCustomerCardNo(),
                        Notifications.getNotificationFrom(invoice)
                ))
        ;

        KGroupedStream<String, Notification> grouped = source.groupByKey(Grouped.with(AppSerdes.String(), AppSerdes.Notification()));
        KTable<String, Notification> dest = grouped.reduce((oldValue, newValue) -> {
            newValue.setTotalLoyaltyPoints(newValue.getEarnedLoyaltyPoints() + oldValue.getTotalLoyaltyPoints());
            return newValue;
        });
        dest.toStream().to(AppConfigs.notificationTopic, Produced.with(AppSerdes.String(), AppSerdes.Notification()));




        logger.info("Starting Stream");
        KafkaStreams stream = new KafkaStreams(builder.build(), props);
        stream.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Stopping Streams");
            stream.cleanUp();
        }));
    }
}
