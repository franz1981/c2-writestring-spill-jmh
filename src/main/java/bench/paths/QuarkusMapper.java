package bench.paths;

import java.util.List;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import bench.paths.beans.ExtendedPerson;

/**
 * The {@code JsonMapper} and {@code ObjectWriter} exactly as the Quarkus application builds them
 * with default configuration and no customizer of its own:
 * <ul>
 * <li>{@code JsonMapperProducer}: {@code JsonMapper.builder(JsonFactory.builder()...build())};</li>
 * <li>{@code ConfigurationCustomizer} with {@code JacksonBuildTimeConfig} defaults: it enables
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES}, {@code FAIL_ON_TRAILING_TOKENS}, {@code FAIL_ON_EMPTY_BEANS},
 * {@code WRITE_DURATIONS_AS_TIMESTAMPS}; every other option is left at Jackson's default
 * (timezone UTC = Jackson's default, no naming strategy, no inclusion override, no mixins);</li>
 * <li>{@code JacksonMessageBodyWriterUtil.createDefaultWriter}: {@code mapper.writer()} without
 * {@code AUTO_CLOSE_TARGET} and {@code FLUSH_PASSED_TO_STREAM};</li>
 * <li>{@code BasicServerJacksonMessageBodyWriter.getWriter}: {@code forType(rootType)} with the
 * resource method's generic return type, {@code List<ExtendedPerson>}.</li>
 * </ul>
 * Known difference: Quarkus installs Vert.x's {@code HybridJacksonPool} as the factory's
 * {@code RecyclerPool}; this uses Jackson's default pool.
 */
public final class QuarkusMapper {

    private QuarkusMapper() {}

    public static JsonMapper.Builder builder() {
        return JsonMapper.builder(JsonFactory.builder().build())
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
    }

    /** {@code createDefaultWriter(mapper).forType(List<ExtendedPerson>)}. */
    public static ObjectWriter listWriter(ObjectMapper mapper) {
        ObjectWriter writer = mapper.writer()
                .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .without(StreamWriteFeature.FLUSH_PASSED_TO_STREAM);
        return writer.forType(writer.getTypeFactory().constructCollectionType(List.class, ExtendedPerson.class));
    }
}
