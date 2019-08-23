package io.micrometer.core.ipc.http;

class OkHttpSenderTests extends AbstractHttpSenderTests {
    @Override
    void setHttpSender() {
        this.httpSender = new OkHttpSender();
    }
}
