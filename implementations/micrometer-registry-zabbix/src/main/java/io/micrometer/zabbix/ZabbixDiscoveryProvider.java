/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.zabbix;

import java.util.Collection;

/**
 * Provide {@link ZabbixDiscoveryItem}s after calling {@link #visit(ZabbixDataItem)}
 * on each {@link ZabbixDataItem} that will be send to Zabbix this step.
 */
public interface ZabbixDiscoveryProvider {

    /**
     * @param dataItem Item that will be send to Zabbix.
     * @return The {@link ZabbixDataItem} that is passed to the function.
     */
    ZabbixDataItem visit(ZabbixDataItem dataItem);

    /**
     * @return The discovery items that are generated after visiting the {@link ZabbixDataItem} for this step.
     */
    Collection<ZabbixDiscoveryItem> takeItems();

}
