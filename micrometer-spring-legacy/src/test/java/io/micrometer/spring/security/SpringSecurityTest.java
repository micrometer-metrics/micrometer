/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.spring.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    // changed to spring.security.* in Boot 2
    "security.user.password=foo"
})
public class SpringSecurityTest {
    
    @Autowired
    private MockMvc mvc;

    @Autowired
    private MeterRegistry registry;

    @Test
    @WithMockUser
    public void securityAllowsAccess() throws Exception {
        mvc.perform(get("/api/secured")).andExpect(status().isOk());

        registry.get("http.server.requests")
            .tags("status", "200")
            .timer();
    }

    @Test
    public void securityBlocksAccess() throws Exception {
        mvc.perform(get("/api/secured")).andExpect(status().isUnauthorized());

        registry.get("http.server.requests")
            .tags("status", "401")
            .timer();
    }

    @SpringBootApplication(scanBasePackages = "ignore")
    @Import(Controller.class)
    static class SecurityApp {
        @Bean
        public MeterRegistry registry() {
            return new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        }
    }

    @RestController
    static class Controller {
        @GetMapping("/api/secured")
        public String secured() {
            return "got in";
        }
    }
}
