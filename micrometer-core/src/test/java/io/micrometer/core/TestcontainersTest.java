package io.micrometer.core;

import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// TODO delete this class - only used for verifying Testcontainers tests on CircleCI build
@Testcontainers
class TestcontainersTest {

    @Container
    ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:6.4.1");

    @Test
    void testContainers() {
        assertThat(elasticsearchContainer.isRunning()).isTrue();
    }
}
