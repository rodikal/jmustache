//
// JMustache - A Java implementation of the Mustache templating language
// http://github.com/samskivert/jmustache/blob/master/LICENSE

package com.samskivert.mustache;

import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThreadSafetyTest {

    private static final String templateA = "Template A content\n{{> templateB}}";
    private static final String templateB = "Template B content";

    private static class CountingLoader implements Mustache.TemplateLoader {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override public Reader getTemplate (String name) throws Exception {
            counter.incrementAndGet();
            if (!"templateB".equals(name)) {
                throw new IllegalArgumentException();
            }
            return new StringReader(templateB);
        }

        public int counter () {
            return counter.get();
        }
    }

    @Test
    public void testTemplateLoading () throws InterruptedException {
        // 4096 works but is disabled as it makes the test take a long time
        for (int ii : new int[] { 1, 2, 4, 8, 16, 32, 256, 1024/* , 4096 */ }) {
            templateLoadingIsThreadSafe(ii);
        }
    }

    void templateLoadingIsThreadSafe (int nThreads) throws InterruptedException {
        final CyclicBarrier barrier = new CyclicBarrier(nThreads);
        final CountingLoader loader = new CountingLoader();
        final Mustache.Compiler compiler = Mustache.compiler().withLoader(loader);
        final Template template = compiler.compile(new StringReader(templateA));

        final List<Thread> threads = new LinkedList<>();
        for (int i = 0; i < nThreads; i++) {
            final Thread thread = new Thread(() -> {
                try {
                    barrier.await();
                    final String value = template.execute(Map.of());
                    if (!value.contains("Template A content\nTemplate B content")) {
                        fail("Invalid template result " + value);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(1, loader.counter());
    }
}
