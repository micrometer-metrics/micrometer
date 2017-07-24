package io.micrometer.spring.samples.components;

import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class PersonController {
    private List<String> people = Arrays.asList("mike", "suzy");

    @GetMapping("/api/people")
    @Timed
    public List<String> allPeople() {
        return people;
    }
}
