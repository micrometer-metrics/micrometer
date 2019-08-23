package io.micrometer.core.ipc.http;

class HttpUrlConnectionSenderTests extends AbstractHttpSenderTests {
    @Override
    void setHttpSender() {
        this.httpSender = new HttpUrlConnectionSender();
    }
}
