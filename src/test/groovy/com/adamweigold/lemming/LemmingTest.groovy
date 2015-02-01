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

/**
 * Created by adam on 1/31/2015.
 */
class LemmingTest extends GroovyTestCase {
    void testInvoke(){
        Lemming<Foo> lemming = new Lemming()
        lemming = lemming.add({
            return Foo
        } as Callable)
        lemming = lemming.add {
            return Foo
        }
        Collection<Foo> foos = lemming.invoke()
        assertEquals 2, foos.size()
    }

    void testDefaultExceptionHandling(){
        Lemming<Foo> lemming = new Lemming()
        lemming.add ({
            throw new RuntimeException()
        } as Callable)
        Collection<Foo> foos = lemming.invoke()
        assertTrue foos.empty
    }

    void testFailAllExceptionHandling(){
        shouldFail(RuntimeException){
            Lemming.create().add{
                throw new RuntimeException()
            }.failOnAnyException().invoke()
        }

    }

    void testClosureExceptionHandling(){
        shouldFail(FooException){
            Lemming.create().add{
                throw new RuntimeException()
            }.setExceptionStrategy {
                throw new FooException()
            }.invoke()
        }
    }

    void testCollectionReturned(){
        def results = Lemming.create().add {
            return new Foo()
        }.add {
            return [new Foo(), new Foo()]
        }.invoke()
        assertEquals 3, results.size()
    }

    void testDefaultTimeout(){
        Lemming.create().add {
            Thread.sleep(10000L)
        }.setTimeout(10L).invoke()
    }

    void testFailAllTimeout(){
        shouldFail(CancellationException){
            Lemming.create().add {
                Thread.sleep(10000L)
            }.setTimeout(10L).failOnAnyTimeout().invoke()
        }
    }

    class Foo {}

    class FooException extends RuntimeException{}
}
