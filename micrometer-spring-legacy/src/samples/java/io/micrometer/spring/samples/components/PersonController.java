/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.samples.components;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class PersonController {
    private List<String> people = Arrays.asList("mike", "suzy");

    @GetMapping("/api/people")
    @Timed(percentiles = true)
    public List<String> allPeople() {
        return people;
    }

    @GetMapping("/api/fail")
    public String fail() {
        return new CommandHelloFailure("World").execute();
    }

    @GetMapping("/api/ok")
    public String ok() {
        return new CommandHelloSuccess("World").execute();
    }

    public static class CommandHelloFailure extends HystrixCommand<String> {

        private final String name;

        public CommandHelloFailure(String name) {
            super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
            this.name = name;
        }

        @Override
        protected String run() {
            throw new RuntimeException("Boom");
        }

        @Override
        protected String getFallback() {
            return "Hello Failure " + name + "!";
        }
    }

    public static class CommandHelloSuccess extends HystrixCommand<String> {

        private final String name;

        public CommandHelloSuccess(String name) {
            super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
            this.name = name;
        }

        @Override
        protected String run() {
            return "OK";
        }

        @Override
        protected String getFallback() {
            return "Hello Failure " + name + "!";
        }
    }
}
