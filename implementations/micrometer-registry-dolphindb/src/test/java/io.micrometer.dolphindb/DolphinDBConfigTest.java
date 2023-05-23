package io.micrometer.dolphindb;

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.Test;
//import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DolphinDBConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final DolphinDBConfig config = props::get;

//    @Test
//    public void invalid() {
//        props.put("dolphindb.uri", "bad");
////        props.put("dolphindb.flavor", "bad");
//
//        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
//                .containsExactlyInAnyOrder("must be a valid URL");
//    }

    @Test
    public void valid() {
        assertThat(config.validate().isValid()).isTrue();
    }

}
