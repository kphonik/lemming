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
import java.util.concurrent.TimeUnit

/**
 * Created by adam on 1/31/2015.
 */
class Lemming<T> {

    private ExecutorService executorService

    private ArrayList<Callable<T>> callables = new ArrayList<>()

    private Long timeout

    private Integer threadCount

    private ExceptionStrategy exceptionStrategy

    private ExceptionStrategy timeoutStrategy

    public static Lemming<T> create(){
        return new Lemming<T>()
    }

    public Lemming<T> setExecutor(ExecutorService executor){
        this.executorService = executor;
        return this
    }

    public Lemming<T> add(Callable<T> callable){
        callables << callable;
        return this
    }

    public Lemming<T> add(Closure<T> closure){
        callables << {closure.call()} as Callable<T>
        return this
    }

    public Lemming<T> setTimeout(long timeout){
        this.timeout = timeout;
        return this
    }

    public Lemming<T> setThreadCount(int threadCount){
        this.threadCount = threadCount;
        return this
    }

    public Lemming<T> setExceptionStrategy(ExceptionStrategy exceptionStrategy){
        this.exceptionStrategy = exceptionStrategy
        return this
    }

    public Lemming<T> setExceptionStrategy(Closure exceptionStrategy){
        this.exceptionStrategy = exceptionStrategy as ExceptionStrategy
        return this
    }

    public Lemming<T> failOnAnyException(){
        this.exceptionStrategy = FAIL_ON_ANY_EXCEPTION
        return this
    }

    public Lemming<T> quietlyHandleAllExeptions(){
        this.exceptionStrategy = QUIETY_HANDLE_EXCEPTIONS
        return this
    }

    public Lemming<T> setTimeoutStrategy(ExceptionStrategy timeoutStrategy){
        this.timeoutStrategy = timeoutStrategy
        return this
    }

    public Lemming<T> setTimeoutStrategy(Closure timeoutStrategy){
        this.timeoutStrategy = timeoutStrategy as ExceptionStrategy
        return this
    }

    public Lemming<T> failOnAnyTimeout(){
        this.timeoutStrategy = FAIL_ON_ANY_EXCEPTION
        return this
    }

    public Lemming<T> quietlyHandleAllTimeouts(){
        this.timeoutStrategy = QUIETY_HANDLE_EXCEPTIONS
        return this
    }

    public Collection<T> invoke(){
        initInvoke()
        def results = []
        try {
            def futures;
            if (timeout == null) {
                futures = executorService.invokeAll(callables)
            } else {
                futures = executorService.invokeAll(callables, timeout, TimeUnit.MILLISECONDS)
            }
            for (Future future : futures) {
                try {
                    def result = future.get()
                    if (result instanceof Collection) {
                        results.addAll(result)
                    } else {
                        results << future.get()
                    }
                } catch (ExecutionException e) {
                    exceptionStrategy.handle(e)
                } catch (CancellationException e) {
                    timeoutStrategy.handle(e)
                } catch (InterruptedException e) {
                    //TODO
                }

            }
        } finally {
            executorService.shutdown()
        }
        return results
    }

    private void initInvoke(){
        if (threadCount == null){
            threadCount = callables.size();
        }
        if (executorService == null){
            executorService = Executors.newFixedThreadPool(threadCount)
        }
        if (exceptionStrategy == null){
            exceptionStrategy = QUIETY_HANDLE_EXCEPTIONS
        }
        if (timeoutStrategy == null){
            timeoutStrategy = QUIETY_HANDLE_EXCEPTIONS
        }
    }

    public static ExceptionStrategy QUIETY_HANDLE_EXCEPTIONS = {} as ExceptionStrategy

    public static ExceptionStrategy FAIL_ON_ANY_EXCEPTION = {throwable -> throw throwable} as ExceptionStrategy

}
