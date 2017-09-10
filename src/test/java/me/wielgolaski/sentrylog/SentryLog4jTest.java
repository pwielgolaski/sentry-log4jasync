package me.wielgolaski.sentrylog;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.Event;
import io.sentry.log4j2.SentryAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;


public class SentryLog4jTest {
    private static final Logger log = LogManager.getLogger(SentryLog4jTest.class);

    private List<Event> events = new ArrayList<>();

    @Before
    public void init() {
        SentryClient client = Sentry.init("http://public:private@localhost:8080/1");
        client.addShouldSendEventCallback(e -> {
            events.add(e);
            return false;
        });
        client.addBuilderHelper(eventBuilder -> eventBuilder.withTag("eventBuilderThread", Thread.currentThread().getName()));
    }

    @Test
    public void shouldFollowThreadWithAsync() {
        ExecutorService service = Executors.newFixedThreadPool(5);
        IntStream.range(1, 50).forEach(i -> service.submit(this::doIt));

        assertThat(events).isNotEmpty().allSatisfy(event -> {
            Object threadName = event.getExtra().get(SentryAppender.THREAD_NAME);
            Map<String, String> tags = event.getTags();
            Object sentryContextThreadName = tags.get("sentryContextThreadName");
            Object eventBuilderThread = tags.get("eventBuilderThread");
            System.out.println(String.format("log4j-event-threadName=%s, sentryContextThreadName=%s, eventBuilderThread=%s",
                    threadName, sentryContextThreadName, eventBuilderThread));
            assertThat(eventBuilderThread).as("Thread name '%s' vs eventBuilderThread '%s'", threadName, eventBuilderThread).isEqualTo(threadName);
            assertThat(sentryContextThreadName).as("Thread name '%s' vs sentryContextThreadName '%s'", threadName, sentryContextThreadName).isEqualTo(threadName);
        });
    }

    public void doIt() {
        Thread currentThread = Thread.currentThread();
        Sentry.getContext().addTag("sentryContextThreadName", currentThread.getName());
        log.warn("doIt - error");
        Sentry.getContext().clear();
    }
}
