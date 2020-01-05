package com.example.prometheuspushgateway;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
public class HelloController {

  private final MeterRegistry meterRegistry;

  public HelloController(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @GetMapping
  public String get() {
    Counter counter = Counter.builder("hello-counter").register(meterRegistry);
    counter.increment();
    return "Hello, counter = " + counter.count();
  }

}