package guru.learningjournal.kafka.examples;

import guru.learningjournal.kafka.examples.serde.AppSerdes;
import guru.learningjournal.kafka.examples.types.DepartmentAggregate;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

public class KStreamAggDemo {
    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, AppConfigs.applicationID);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfigs.bootstrapServers);
        props.put(StreamsConfig.STATE_DIR_CONFIG, AppConfigs.stateStoreLocation);// store is needed

        StreamsBuilder streamsBuilder = new StreamsBuilder();
        // Initialize the store, why we need this ?
        // reducce(), creates new store with default values, but does not allow to change the type & hence we need to use a map
        // Here as we see we are getting employee as source but changing grouped by department as key
        streamsBuilder.stream(AppConfigs.topicName,
                        Consumed.with(AppSerdes.String(), AppSerdes.Employee()))
                .groupBy((k,v) -> v.getDepartment(), Grouped.with(AppSerdes.String(),AppSerdes.Employee()))
                .aggregate(
                        //Initializer
                        ()-> new DepartmentAggregate()
                                .withEmployeeCount(0)
                                .withTotalSalary(0)
                                .withAvgSalary(0D),
                        //Aggregator
                        (k,v,aggValue) ->
                                new DepartmentAggregate()
                                        .withEmployeeCount(aggValue.getEmployeeCount()+1)
                                        .withTotalSalary(aggValue.getTotalSalary() + v.getSalary())
                                        .withAvgSalary((aggValue.getTotalSalary()+v.getSalary())/(aggValue.getEmployeeCount()+1D)),
                        //Serializer
                        Materialized.<String,DepartmentAggregate, KeyValueStore<Bytes, byte[]>>as(AppConfigs.stateStoreName)
                                .withKeySerde(AppSerdes.String())
                                .withValueSerde(AppSerdes.DepartmentAggregate())
                ).toStream().print(Printed.<String, DepartmentAggregate>toSysOut().withLabel("Department Salary Average"));


        KafkaStreams streams = new KafkaStreams(streamsBuilder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Stopping Streams");
            streams.close();
        }));

    }
}