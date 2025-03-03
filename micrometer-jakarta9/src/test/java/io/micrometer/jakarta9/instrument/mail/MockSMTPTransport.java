/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.jakarta9.instrument.mail;

import jakarta.mail.*;

public class MockSMTPTransport extends Transport {

    public static MockSMTPTransportListener LISTENER;

    public MockSMTPTransport(Session session, URLName urlname) {
        super(session, urlname);
        LISTENER.onConstructor(session, urlname);
    }

    @Override
    public synchronized void connect(String host, int port, String user, String password) throws MessagingException {
        LISTENER.onConnect(host, port, user, password);
    }

    @Override
    public void sendMessage(Message msg, Address[] addresses) throws MessagingException {
        LISTENER.onSendMessage(msg, addresses);
    }

    @Override
    public synchronized void close() throws MessagingException {
        LISTENER.onClose();
    }

}
