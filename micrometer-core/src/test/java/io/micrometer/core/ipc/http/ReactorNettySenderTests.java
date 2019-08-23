package io.micrometer.core.ipc.http;

class ReactorNettySenderTests extends AbstractHttpSenderTests {
    @Override
    void setHttpSender() {
        this.httpSender = new ReactorNettySender();
    }
}
