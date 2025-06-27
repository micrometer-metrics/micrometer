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
package io.micrometer.jakarta9.instrument.mail;

import jakarta.mail.*;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;

/**
 * Mock implementation of the SMTP Transport class for testing purposes. This class allows
 * us to simulate the behavior of an SMTP transport without actually sending emails.
 *
 * @author famaridon
 */
public class MockSMTPTransport extends Transport {

    // Needs to be static since it is created by the Java Mail implementation,
    // and we don't have access to the instance
    private static final Listener LISTENER = mock(Listener.class);

    public MockSMTPTransport(Session session, URLName urlname) {
        super(session, urlname);
        LISTENER.onConstructor(session, urlname);
    }

    static Listener resetAndGetGlobalListener() {
        Mockito.reset(LISTENER);
        return LISTENER;
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
    public synchronized void close() {
        LISTENER.onClose();
    }

    interface Listener {

        void onConstructor(Session session, URLName urlname);

        void onConnect(String host, int port, String user, String password) throws MessagingException;

        boolean onSendMessage(Message msg, Address[] addresses) throws MessagingException;

        void onClose();

    }

}
