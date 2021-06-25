import common.sources.BoundedRateGenerator;
import common.utils.Utilities;
import common.datatypes.Rate;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.SimpleVersionedStringSerializer;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;

import java.util.concurrent.TimeUnit;

public class SameSchema {
    private static final String OUTPUT_DIRECTORY = ".\\output\\useCase\\splitStreamByKey\\SameSchema";

    public static void main(String[] args) throws Exception {
        Utilities.removeDir(OUTPUT_DIRECTORY); // clear output directory
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // Streaming part files are only moved to "finished" state upon checkpointing, so must enable checkpointing
        env.enableCheckpointing(10000); // checkpoint every 10s

        DataStream<Rate> input = env.addSource(new BoundedRateGenerator()).keyBy(x -> (x.id % 10)); // Generates 100 records, 1 record per second

        OutputFileConfig config = OutputFileConfig // More convenient to open if all part files have the same suffix
                .builder()
                .withPartPrefix("prefix")
                .withPartSuffix(".ext")
                .build();

        FileSink<Rate> sink = FileSink
                .<Rate>forRowFormat(new Path(OUTPUT_DIRECTORY), new SimpleStringEncoder<>("UTF-8"))
                .withBucketAssigner(new KeyBucketAssigner()) // Custom bucket assigner that assigns elements to buckets (subdirectories) based on their key
                .withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withRolloverInterval(TimeUnit.SECONDS.toMillis(60))
                                .withInactivityInterval(TimeUnit.SECONDS.toMillis(1))
                                .withMaxPartSize(1024)
                                .build()
                )
                .withOutputFileConfig(config)
                .build();

        input.sinkTo(sink);

        env.execute();
    }

    /*
    Assigns elements to buckets based on their key.
     */
    public static class KeyBucketAssigner implements BucketAssigner<Rate, String> {

        @Override
        public String getBucketId(Rate element, Context context) {
            System.out.println("element.id = " + element.id + ", element.timestamp = " + element.timestamp);
            return String.valueOf(element.id % 10);
        }

        @Override
        public SimpleVersionedSerializer<String> getSerializer() {
            return SimpleVersionedStringSerializer.INSTANCE;
        }
    }
}
