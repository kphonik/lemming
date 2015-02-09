/*
 * Copyright 2015 Adam J. Weigold.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.adamweigold.lemming

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

/**
 * Created by adam on 1/31/2015.
 */
class LemmingTest extends GroovyTestCase {
    void testInvoke() {
        Lemming<Collected> lemming = new Lemming()
        lemming = lemming.add( {
            Collected
        } as Callable)
        lemming = lemming.add {
            Collected
        }
        Collection<Collected> foos = lemming.collect()
        assert 2 == foos.size()
    }

    void testDefaultExceptionHandling() {
        Lemming<Collected> lemming = new Lemming()
        lemming.add ( {
            throw new RuntimeException()
        } as Callable)
        Collection<Collected> foos = lemming.collect()
        assert foos.empty
    }

    void testFailAllExceptionHandling() {
        shouldFail(RuntimeException) {
            Lemming.makeLemming().add {
                throw new RuntimeException()
            }.failOnAnyException().collect()
        }

    }

    void testClosureExceptionHandling() {
        shouldFail(FooException) {
            Lemming.makeLemming().add {
                throw new RuntimeException()
            }.setExceptionStrategy {
                throw new FooException()
            }.collect()
        }
    }

    void testCollectionReturned() {
        def results = Lemming.makeLemming().add {
            Collected
        }.add {
            [Collected, Collected]
        }.collect()
        assert 3 == results.size()
    }

    void testDefaultTimeout() {
        def results = Lemming.makeLemming().add {
            Thread.sleep(10000L)
        }.setTimeout(10L).collect()
        assert 0 == results.size()
    }

    void testFailAllTimeout() {
        shouldFail(CancellationException) {
            Lemming.makeLemming().add {
                Thread.sleep(10000L)
            }.setTimeout(10L).failOnAnyTimeout().collect()
        }
    }

    void testCustomExecutorNotShutdown() {
        def executorService = Executors.newCachedThreadPool()
        try {
            Lemming.makeLemming().add {
                Collected
            }.setExecutor(executorService).collect()
            assert !executorService.isShutdown()
        } finally {
            executorService.shutdown()
        }
    }

    void testCustomExecutorShutdown() {
        def executorService = Executors.newCachedThreadPool()
        try {
            Lemming.makeLemming().add {
                Collected
            }.setExecutor(executorService).setShutdownExecutorService(true).collect()
            assert executorService.isShutdown()
        } finally {
            executorService.shutdown()
        }
    }

    void testThreadName() {
        List<String> results = Lemming.makeLemming().add {
            Thread.currentThread().name
        }.setThreadNameFormat('TestFormat-%s').collect()
        assert results.first().startsWith('TestFormat-')
    }

    void testThreadPriority() {
        List<Integer> results = Lemming.makeLemming().add {
            Thread.currentThread().priority
        }.setPriority(1).collect()
        assert 1 == results.first()
    }

    void testThreadUnderMin() {
        shouldFail(IllegalArgumentException) {
            Lemming.makeLemming().setPriority(Thread.MIN_PRIORITY - 1)
        }
    }

    void testThreadAboveMax() {
        shouldFail(IllegalArgumentException) {
            Lemming.makeLemming().setPriority(Thread.MAX_PRIORITY + 1)
        }
    }

    void testThreadDaemon() {
        List<Boolean> results = Lemming.makeLemming().add {
            Thread.currentThread().isDaemon()
        }.setDaemon(true).collect()
        assert results.first()
    }

    void testNotThreadDaemon() {
        List<Boolean> results = Lemming.makeLemming().add {
            Thread.currentThread().isDaemon()
        }.setDaemon(false).collect()
        assert !results.first()
    }

    void testThreadCount() {
        List<String> results = Lemming.makeLemming().add {
            Thread.currentThread().name
        }.add {
            Thread.currentThread().name
        }.setThreadCount(1).collect()
        assert 2 == results.size()
        results.each { result ->
            assert result.endsWith('-1')
        }
    }

    void testBackingThreadFactory() {
        List<String> results = Lemming.makeLemming().add {
            Thread.currentThread().name
        }.setThreadFactory { runnable ->
            def thread = new Thread(runnable)
            thread.setName('CustomThreadFactory')
            thread
        }.collect()
        assert 'CustomThreadFactory' == results.first()
    }

    void testQuietlyHandleAllExceptions() {
        def results = Lemming.makeLemming().add {
            throw new RuntimeException()
        }.quietlyHandleAllExeptions().collect()
        assert results.empty
    }

    void testQuietlyHandleAllTimeouts() {
        def results = Lemming.makeLemming().add {
            Thread.sleep(10000L)
        }.setTimeout(1L).quietlyHandleAllTimeouts().collect()
        assert results.empty
    }

    void testCustomTimeoutStrategy() {
        shouldFail(FooException) {
            Lemming.makeLemming().add {
                Thread.sleep(10000L)
            }.setTimeout(1L).setTimeoutStrategy { exception ->
                throw new FooException()
            }.collect()
        }
    }

    class FooException extends RuntimeException { }
}
