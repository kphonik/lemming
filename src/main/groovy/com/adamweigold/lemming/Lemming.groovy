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
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Provides a builder pattern for asynchronously collecting results from one or more {@link Callable}s.
 * <p>
 * This can be invoked in the following manner from Java:
 * <pre>
 * <code>Lemming{@code <Collected>} lemming = Lemming.makeLemming();
 * Collection{@code <Collected>} results = lemming.add(new Callable{@code <Collected>}() {
 *     public Collected call() throws Exception {
 *       return new Collected();
 *     }
 *   }
 * ).add(new Callable{@code <Collection<Collected>>}() {
 *     public Collection{@code <Collected>} call() throws Exception {
 *       return Arrays.asList(new Collected(), new Collected());
 *     }
 * }).collect();</code>
 * </pre>
 * Since {@link Callable} is a {@link FunctionalInterface}, this can be simplified in Groovy:
 * <pre>
 * <code>def results = Lemming.makeLemming().add {
 *   new Collected()
 * }.add {
 *   [new Collected(), new Collected()]
 * }.collect()</code>
 * </pre>
 *
 * @param < T > the type of result to be collected
 *
 * @author Adam J. Weigold <adam@adamweigold.com>
 */
class Lemming<T> {

    /**
     * An {@link ExceptionStrategy} that swallows all exceptions
     */
    public static final ExceptionStrategy QUIETLY_HANDLE_EXCEPTIONS = { } as ExceptionStrategy

    /**
     * An {@link ExceptionStrategy} that rethrows the exception given to it
     */
    public static final ExceptionStrategy FAIL_ON_ANY_EXCEPTION = { throwable -> throw throwable } as ExceptionStrategy

    private ExecutorService executorService

    private Boolean shutdownExecutorService

    private final List<Callable<T>> callables = []

    private Long timeout

    private Integer threadCount

    private String threadNameFormat = null

    private Boolean daemon = null

    private Integer priority = null

    private ThreadFactory backingThreadFactory = null

    private ExceptionStrategy exceptionStrategy

    private ExceptionStrategy timeoutStrategy

    /**
     * Factory method to return a new Lemming instance
     *
     * @return a new Lemming instance
     */
    static <T> Lemming<T> makeLemming() {
        new Lemming<T>()
    }

    /**
     * Overrides usage of the default executor service.  Furthermore setting this will also cause {@link #collect} to
     * default to not shutting down the executor service if {@link #setShutdownExecutorService} is not invoked.
     * <p>
     * Setting this will cause settings to the following to be ignored as the {@link ThreadFactory} in the
     * {@link ExecutorService} is not overwritten:
     *     <ul>
     *         <li>{@link #setThreadCount}</li>
     *         <li>{@link #setThreadNameFormat}</li>
     *         <li>{@link #setDaemon}</li>
     *         <li>{@link #setPriority}</li>
     *         <li>{@link #setThreadUncaughtExceptionHandler}</li>
     *         <li>{@link #setThreadFactory}</li>
     *     </ul>
     *
     * @param executor the {@link ExecutorService} to use during collection.
     * @return this for the builder pattern
     */
    Lemming<T> setExecutor(ExecutorService executor) {
        this.executorService = executor
        this
    }

    /**
     * Sets if the {@link ExecutorService} used during {@link #collect} is shutdown.  Defaults to {@code true} if
     * {@link #setExecutor} is not invoked, and {@code false} if it is.
     *
     * @param shutdown boolean setting for if {@link #collect} should shutdown the {@link ExecutorService}
     * @return this for the builder pattern
     */
    Lemming<T> setShutdownExecutorService(boolean shutdown) {
        this.shutdownExecutorService = shutdown
        this
    }

    /**
     * Adds an additional callable who's results will be collected.
     *
     * @param callable a callable that returns either <T> or Collection<T>
     * @return this for the builder pattern
     */
    Lemming<T> add(Callable callable) {
        callables << callable
        this
    }

    /**
     * Sets the maximum time to wait for execution of all {@link Callable}s by the executor service in
     * {@link TimeUnit#MILLISECONDS}.
     *
     * @param the maximum time to wait
     * @return this for the builder pattern
     */
    Lemming<T> setTimeout(long timeout) {
        this.timeout = timeout
        this
    }

    /**
     * Sets the size of the thread pool to use when collecting.  Defaults to the number of {@link Callable}s added.
     * This setting is ignored when using {@link #setExecutor}.
     * @param threadCount the size of the thread pool to use when collecting
     * @return this for the builder pattern
     */
    Lemming<T> setThreadCount(int threadCount) {
        this.threadCount = threadCount
        this
    }

    /**
     * Sets the naming format to use when naming threads ({@link Thread#setName})
     * which are created during {@link #collect}.  This setting is ignored when using {@link #setExecutor}.
     *
     * @param threadNameFormat a {@link String#format(String, Object...)}-compatible
     *     format String, to which a unique integer (0, 1, etc.) will be supplied
     *     as the single parameter. This integer will be unique to the built
     *     instance of the ThreadFactory and will be assigned sequentially. For
     *     example, {@code "rpc-pool-%d"} will generate thread names like
     *     {@code "rpc-pool-0"}, {@code "rpc-pool-1"}, {@code "rpc-pool-2"}, etc.
     * @return this for the builder pattern
     */
    Lemming<T> setThreadNameFormat(String threadNameFormat) {
        String.format(threadNameFormat, 0) // fail fast if the format is bad or null
        this.threadNameFormat = threadNameFormat
        this
    }

    /**
     * Sets daemon or not for new threads created during {@link #collect}.  This setting is ignored when using
     * {@link #setExecutor}.
     *
     * @param daemon whether or not new Threads created during {@link #collect}
     *     will be daemon threads
     * @return this for the builder pattern
     */
    Lemming<T> setDaemon(boolean daemon) {
        this.daemon = daemon
        this
    }

    /**
     * Sets the priority for new threads created during {@link #collect}.  This setting is ignored when using
     * {@link #setExecutor}.
     *
     * @param priority the priority for new Threads created with during
     *     {@link #collect}
     * @return this for the builder pattern
     */
    Lemming<T> setPriority(int priority) {
        // Thread#setPriority() already checks for validity. These error messages
        // are nicer though and will fail-fast.
        if (priority < Thread.MIN_PRIORITY) {
            throw new IllegalArgumentException("Thread priority ($priority) must be >= $Thread.MIN_PRIORITY")
        }
        if (priority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException("Thread priority ($priority) must be <= $Thread.MAX_PRIORITY")
        }
        this.priority = priority
        this

    }

    /**
     * Sets the backing {@link ThreadFactory} for new threads created during
     * {@link #collect}. Threads will be created by invoking #newThread(Runnable) on
     * this backing {@link ThreadFactory}.  This setting is ignored when using
     * {@link #setExecutor}.
     *
     * @param backingThreadFactory the backing {@link ThreadFactory} which will
     *     be delegated to during thread creation.
     * @return this for the builder pattern
     */
    Lemming<T> setThreadFactory(ThreadFactory backingThreadFactory) {
        this.backingThreadFactory = backingThreadFactory
        this
    }

    /**
     * Sets an {@link ExceptionStrategy} that will be invoked for any {@link Callable}s
     * that abort due to throwing an Exception.  Note that any exceptions thrown from this
     * handler will cancel all unfinished {@link Callable}s.
     *
     * Defaults to {@link Lemming#QUIETLY_HANDLE_EXCEPTIONS}
     *
     * @param exceptionStrategy the exception handler that for any {@link ExecutionException}
     *     received from invoking a {@link Callable}
     * @return this for the builder pattern
     */
    Lemming<T> setExceptionStrategy(ExceptionStrategy exceptionStrategy) {
        this.exceptionStrategy = exceptionStrategy
        this
    }

    /**
     * Sets an {@link ExceptionStrategy} that will rethrow any Exception from an aborted {@link Callable}
     * which will cancel all unfinished {@link Callable}s and will rethrown the offending
     * {@link ExecutionException}
     *
     * @return this for the builder pattern
     */
    Lemming<T> failOnAnyException() {
        this.exceptionStrategy = FAIL_ON_ANY_EXCEPTION
        this
    }

    /**
     * Sets an {@link ExceptionStrategy} that will swallow any Exception from an aborted {@link Callable}
     * which will allow all unfinished {@link Callable}s to continue to return results.
     *
     * @return this for the builder pattern
     */
    Lemming<T> quietlyHandleAllExeptions() {
        this.exceptionStrategy = QUIETLY_HANDLE_EXCEPTIONS
        this
    }

    /**
     * Sets an {@link ExceptionStrategy} that will be invoked for any {@link Callable}s
     * that are cancelled due to timing out.
     *
     * Defaults to {@link Lemming#QUIETLY_HANDLE_EXCEPTIONS}
     *
     * @param exceptionStrategy the exception handler that for any {@link Callable}
     *     that times out
     * @return this for the builder pattern
     */
    Lemming<T> setTimeoutStrategy(ExceptionStrategy timeoutStrategy) {
        this.timeoutStrategy = timeoutStrategy
        this
    }

    /**
     * Sets an {@link ExceptionStrategy} that will rethrow from a timed out {@link Callable},
     * effectively stopping any results to be returned from {@link #collect}.
     *
     * @return this for the builder pattern
     */
    Lemming<T> failOnAnyTimeout() {
        this.timeoutStrategy = FAIL_ON_ANY_EXCEPTION
        this
    }

    /**
     * Sets an {@link ExceptionStrategy} that will swallow from a timed out {@link Callable},
     * effectively allowing any finished results to be returned from {@link #collect}.
     *
     * @return this for the builder pattern
     */
    Lemming<T> quietlyHandleAllTimeouts() {
        this.timeoutStrategy = QUIETLY_HANDLE_EXCEPTIONS
        this
    }

    /**
     * Invokes all the {@link Callable}s added and returns all the results as a collection.
     *
     * @return a collection containing all the results from completed {@link Callable}s
     */
    Collection<T> collect() {
        final boolean SHUTDOWN_EXECUTOR_SERVICE = (this.shutdownExecutorService != null) ?
                this.shutdownExecutorService : (this.executorService == null)
        final ExecutorService EXECUTOR_SERVICE = (this.executorService != null) ?
                this.executorService : initExecutorService()
        final ExceptionStrategy EXCEPTION_STRATEGY = (this.exceptionStrategy != null) ?
                this.exceptionStrategy : QUIETLY_HANDLE_EXCEPTIONS
        final ExceptionStrategy TIMEOUT_STRATEGY = (this.timeoutStrategy != null) ?
                this.timeoutStrategy : QUIETLY_HANDLE_EXCEPTIONS
        List<Future> futures
        try {
            def results = []
            if (timeout == null) {
                futures = EXECUTOR_SERVICE.invokeAll(callables)
            } else {
                futures = EXECUTOR_SERVICE.invokeAll(callables, timeout, TimeUnit.MILLISECONDS)
            }
            for (Future future : futures) {
                try {
                    def result = future.get()
                    if (result instanceof Collection) {
                        results.addAll(result)
                    } else {
                        results << result
                    }
                } catch (ExecutionException e) {
                    EXCEPTION_STRATEGY.handle(e)
                } catch (CancellationException e) {
                    TIMEOUT_STRATEGY.handle(e)
                } catch (InterruptedException e) {
                    TIMEOUT_STRATEGY.handle(e)
                }
            }
            results
        } finally {
            if (futures != null) {
                futures.each { future -> future.cancel(true) }
            }
            if (SHUTDOWN_EXECUTOR_SERVICE) {
                EXECUTOR_SERVICE.shutdown()
            }
        }
    }

    @SuppressWarnings('UnnecessaryGetter')
    private ExecutorService initExecutorService() {
        final int THREAD_COUNT = (this.threadCount != null) ? this.threadCount : callables.size()
        final String NAME_FORMAT = this.threadNameFormat
        final Boolean DAEMON = this.daemon
        final Integer PRIORITY = this.priority
        final ThreadFactory BACKING_THREAD_FACTORY =
                (this.backingThreadFactory != null) ? this.backingThreadFactory : Executors.defaultThreadFactory()
        final AtomicLong COUNT = (NAME_FORMAT != null) ? new AtomicLong(0) : null
        Executors.newFixedThreadPool(THREAD_COUNT, { runnable ->
            Thread thread = BACKING_THREAD_FACTORY.newThread(runnable)
            if (NAME_FORMAT != null) {
                thread.setName(String.format(NAME_FORMAT, COUNT.getAndIncrement()))
            }
            if (DAEMON != null) {
                thread.setDaemon(DAEMON)
            }
            if (PRIORITY != null) {
                thread.setPriority(PRIORITY)
            }
            thread
        } as ThreadFactory )
    }

}
