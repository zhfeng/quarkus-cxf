package io.quarkiverse.cxf.deployment.test;

import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;
import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import jakarta.xml.ws.WebServiceException;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.QuarkusUnitTest;

public class ClientReceiveTimeoutTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(HelloService.class, SlowHelloServiceImpl.class))
            .overrideConfigKey("quarkus.cxf.endpoint.\"/hello\".implementor",
                    SlowHelloServiceImpl.class.getName())
            .overrideConfigKey("quarkus.cxf.client.hello.client-endpoint-url", "http://localhost:8081/services/hello")
            .overrideConfigKey("quarkus.cxf.client.hello.service-interface",
                    "io.quarkiverse.cxf.deployment.test.ClientReceiveTimeoutTest$HelloService")
            /* Receive timeout is much shorter than the Thread.sleep() in the endpoint impl. */
            .overrideConfigKey("quarkus.cxf.client.hello.receive-timeout", "100");

    @Inject
    @CXFClient
    HelloService helloService;

    /**
     * Ensures that the client call fails when its ReceiveTimeout is shorter than the intentionally slow endpoint response.
     */
    @Test
    public void expectReceiveTimeout() {
        Assertions
                .assertThatExceptionOfType(WebServiceException.class)
                .isThrownBy(() -> helloService.hello("Joe"))
                .withRootCauseInstanceOf(TimeoutException.class);
    }

    @WebService
    public interface HelloService {

        @WebMethod
        String hello(String person);

    }

    @WebService(endpointInterface = "io.quarkiverse.cxf.deployment.test.ClientReceiveTimeoutTest$HelloService", serviceName = "HelloService")
    public static class SlowHelloServiceImpl implements HelloService {

        @Override
        public String hello(String person) {
            try {
                Thread.sleep(500);
                return "Hello " + person;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

}
