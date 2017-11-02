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
package io.micrometer.core.instrument.filter;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class FilterProperties {
    public static final String INCLUDE = "include";
    public static final String EXCLUDE = "exclude";

    private String defaultCombinedBucketName = "other";
    private String defaultFilter = INCLUDE;
    private Map<String, String> filter = new HashMap<>();
    private Map<String, String> combine = new HashMap<>();

    public String getDefaultFilter() {
        return defaultFilter;
    }

    public void setDefaultFilter(String defaultFilter) {
        this.defaultFilter = defaultFilter;
    }

    public void setDefaultCombinedBucketName(String defaultCombinedBucketName) {
        this.defaultCombinedBucketName = defaultCombinedBucketName;
    }

    public String getDefaultCombinedBucketName() {
        return defaultCombinedBucketName;
    }

    public Map<String, String> getFilter() {
        return filter;
    }

    public Map<String, String> getCombine() {
        return combine;
    }

    public String findMostSpecificRule(String name, Map<String,String> map, String defaultVal) {
        String filterStatus = defaultVal;
        String filterLookup = map.get(name);
        if(filterLookup != null) {
            filterStatus = filterLookup;
        } else if(name.contains(".")) {
            filterStatus = findMostSpecificRule(name.substring(0,name.lastIndexOf(".")), map, defaultVal);
        }

        return filterStatus;
    }

    public String filterStatus(String name) {
        return findMostSpecificRule(name, filter, getDefaultFilter());
    }

    public Iterable<Tag> combineTags(Meter.Id id) {
        String tagRule = findMostSpecificRule(id.getName(), combine, "");
        if(tagRule.isEmpty()) {
            return id.getTags();
        }

        List<Tag> tagRules = Arrays.stream(tagRule.split(";")).map(rule -> {
            String[] values = rule.split("\\|");
            if(values.length == 2) {
                return new ImmutableTag(values[0],values[1]);
            } else {
                return new ImmutableTag(values[0],"");
            }
        }).collect(Collectors.toList());

        Iterable<Tag> filteredTags = StreamSupport.stream(id.getTags().spliterator(), false).map(origTag -> {
            Optional<Tag> tagRuleMatch = tagRules.stream().filter(ruleTag -> origTag.getKey().equalsIgnoreCase(ruleTag.getKey())).findFirst();

            if(!tagRuleMatch.isPresent()) {
                return origTag;
            }

            return new ImmutableTag(origTag.getKey(), combineTagValue(tagRuleMatch.get().getValue(), origTag.getValue()));
        }).collect(Collectors.toList());

        return filteredTags;
    }

    private String combineTagValue(String allowedValues, String value) {
        for(String allowedVal : allowedValues.split(",")) {
            if(allowedVal.equalsIgnoreCase(value)) {
                return value;
            }
        }
        return getDefaultCombinedBucketName();
    }
}
