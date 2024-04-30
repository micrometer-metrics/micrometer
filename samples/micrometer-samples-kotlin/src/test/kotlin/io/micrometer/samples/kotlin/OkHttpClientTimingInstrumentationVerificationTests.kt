/*
 * Copyright 2024 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.samples.kotlin

import io.micrometer.core.instrument.HttpClientTimingInstrumentationVerificationTests
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationDocumentation
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI

internal class OkHttpClientTimingInstrumentationVerificationTests :
    HttpClientTimingInstrumentationVerificationTests<OkHttpClient>() {

    override fun sendHttpRequest(
        instrumentedClient: OkHttpClient,
        method: HttpMethod,
        body: ByteArray?,
        baseUri: URI,
        templatedPath: String,
        vararg pathVariables: String,
    ) {
        val request = Request.Builder().method(method.name, body?.toRequestBody())
            .url(baseUri.toString() + substitutePathVariables(templatedPath, *pathVariables))
            .header(OkHttpMetricsEventListener.URI_PATTERN, templatedPath)
            .build()
        try {
            instrumentedClient.newCall(request).execute().use { }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun clientInstrumentedWithMetrics() = OkHttpClient.Builder()
        .eventListener(OkHttpMetricsEventListener.builder(registry, timerName()).build())
        .build()

    override fun clientInstrumentedWithObservations() = OkHttpClient.Builder()
        .addInterceptor(OkHttpObservationInterceptor.builder(observationRegistry, timerName()).build())
        .build()

    override fun observationDocumentation() = OkHttpObservationDocumentation.DEFAULT
}
