/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link CreateBucketJSONBuilder}.
 *
 * @author Jakub Bednar
 */
class CreateBucketJSONBuilderTest {

    private static final String TEST_BUCKET = "dummy_bucket";
    private static final String TEST_ORG = "020f755c3c082000";

    private final CreateBucketJSONBuilder createBucketJSONBuilder = new CreateBucketJSONBuilder(TEST_BUCKET, TEST_ORG);

    @Test
    void noEmptyBucketName() {
        assertThrows(IllegalArgumentException.class, () -> new CreateBucketJSONBuilder(null, TEST_ORG));
    }

    @Test
    void noEmptyOrgName() {
        assertThrows(IllegalArgumentException.class, () -> new CreateBucketJSONBuilder(TEST_BUCKET, null));
    }

    @Test
    void noEverySeconds() {
        String query = createBucketJSONBuilder.build();
        assertEquals("{\"name\":\"dummy_bucket\",\"orgID\":\"020f755c3c082000\",\"retentionRules\":[]}", query);
    }

    @Test
    void withEverySeconds() {
        String query = createBucketJSONBuilder.setEverySeconds("3600").build();
        assertEquals("{\"name\":\"dummy_bucket\",\"orgID\":\"020f755c3c082000\",\"retentionRules\":[{\"type\":\"expire\",\"everySeconds\":3600}]}", query);
    }
}
