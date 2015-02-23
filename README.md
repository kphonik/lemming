# Lemming

Provides an easy to use builder API for collecting results from multiple threads in Java or Groovy.

[![Build Status](https://travis-ci.org/aweigold/lemming.svg)](https://travis-ci.org/aweigold/lemming)
[![Coverage Status](https://coveralls.io/repos/aweigold/lemming/badge.svg?branch=master)](https://coveralls.io/r/aweigold/lemming?branch=master)

Note this is still in alpha, the API may change and there may be some errors.

## Usage
The Lemming library will collect objects from a list of Callables.  The Callables can return either a single object, or a Collection of them.

The basic usage to the Lemming library is as simple as adding Callables to the Lemming builder

```java
Lemming<Collected> lemming = Lemming.makeLemming();
Collection<Collected> results = lemming.add(new Callable<Collected>() {
    public Collected call() throws Exception {
        return new Collected();
    }
}
).add(new Callable<Collection<Collected>>() {
    public Collection<Collected> call() throws Exception {
        return Arrays.asList(new Collected(), new Collected());
    }
}).collect();
```

Because Callable is a functional interface, this is even easier in Groovy

```groovy
Lemming<Collected> lemming = new Lemming()
lemming = lemming.add( {
    Collected
} as Callable)
lemming = lemming.add {
    [Collected, Collected]
}
Collection<Collected> foos = lemming.collect()
```

By default, Lemming will swallow any exceptions thrown by any of your Callables and collect results from the succeeding ones, however, if you want to fail collection if any fail, you can specify so in the builder

```groovy
void testFailAllExceptionHandling() {
    shouldFail(RuntimeException) {
        Lemming.makeLemming().add {
            throw new RuntimeException()
        }.failOnAnyException().collect()
    }
}
```

Custom exception handling can also be specified by providing an ExceptionStrategy.  An ExceptionStrategy is a Functional Interface that just needs an to implement

```java
void handle(Throwable e)
```

An example in groovy

```groovy
void testClosureExceptionHandling() {
    shouldFail(FooException) {
        Lemming.makeLemming().add {
            throw new RuntimeException()
        }.setExceptionStrategy {
            throw new FooException()
        }.collect()
    }
}
```

When working with Callables, it is often useful to set a timeout for execution

```groovy
void testDefaultTimeout() {
    def results = Lemming.makeLemming().add {
        Thread.sleep(10000L)
    }.setTimeout(10L).collect()
    assert 0 == results.size()
}
```

Likewise, it is useful to specify to fail the collection when any of the Callables do timeout

```groovy
void testFailAllTimeout() {
    shouldFail(CancellationException) {
        Lemming.makeLemming().add {
            Thread.sleep(10000L)
        }.setTimeout(10L).failOnAnyTimeout().collect()
    }
}
```

Thread timeouts are handled separately from standard Exceptions, and by default are swallowed, however you may specify an ExceptionHandler for timeouts

```groovy
void testCustomTimeoutStrategy() {
    shouldFail(FooException) {
        Lemming.makeLemming().add {
            Thread.sleep(10000L)
        }.setTimeout(1L).setTimeoutStrategy { exception ->
            throw new FooException()
        }.collect()
    }
}
```

Lemming will automatically create a fixed thread pool ExecutorService for you with a thread for each Callable, and by default, it will shut it down, however if you wish to have more control over execution, you can provide an ExecutorService (and the default will change to not automatically shut it down)

```groovy
def executorService = Executors.newCachedThreadPool()
Lemming.makeLemming().add {
    Collected
}.setExecutor(executorService).collect()
```

Lemming will also allow you to override the default shutdown policy of the ExecutorService as well (whether using the auto created ExecutorService or a provided one)

```groovy
Lemming.makeLemming().add {
    Collected
}.setShutdownExecutorService(true).collect()
```

It's always useful to name your threads so while troubleshooting, you can more easily identify what the process is.  Lemming allows you to specify a thread name format when using it's default ExecutorService.  (Note: this is ignored when you provide an ExecutorService)

```groovy
void testThreadName() {
    List<String> results = Lemming.makeLemming().add {
        Thread.currentThread().name
    }.setThreadNameFormat('TestFormat-%s').collect()
    assert results.first().startsWith('TestFormat-')
}
```

Advanced use cases can also set the thread priority, or if the threads will be daemon threads

```groovy
void testThreadPriority() {
    List<Integer> results = Lemming.makeLemming().add {
        Thread.currentThread().priority
    }.setPriority(1).collect()
    assert 1 == results.first()
}
void testThreadDaemon() {
    List<Boolean> results = Lemming.makeLemming().add {
        Thread.currentThread().isDaemon()
    }.setDaemon(true).collect()
    assert results.first()
}
```

If you need to control thread creation, but are not concerned with the ExecutorService, a ThreadFactory may be provided

```groovy
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
```

## License

Copyright 2015 Adam J. Weigold.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.