/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument;

/**
 * A provider of tags.
 *
 * @author Marcin Grzejszczak
 */
public interface TagsProvider<T extends Timer.Context> {
    
    Tags getLowCardinalityTags();
    
    Tags getHighCardinalityTags();
    
    default Tags getAllTags() {
        return Tags.concat(getLowCardinalityTags(), getHighCardinalityTags());
    }
    
    boolean supportsContext(Timer.Context context);
}
