/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.samples.jakarta10.mail;

import jakarta.mail.*;

public class MockSMTPTransport extends Transport {

    public MockSMTPTransport(Session session, URLName urlname) {
        super(session, urlname);
    }

    @Override
    public synchronized void connect(String host, int port, String user, String password) throws MessagingException {
    }

    @Override
    public void sendMessage(Message msg, Address[] addresses) throws MessagingException {
    }

    @Override
    public synchronized void close() {
    }

}
