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
package com.adamweigold.lemming;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * Created by adam on 2/8/2015.
 */
public class LemmingJavaTest {

    /**
     * This test is mostly just to check Java styling.  {@see LemmingTest} for full test suite.
     */
    @Test
    public void testJava(){
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
        Assert.assertEquals(3, results.size());
    }
}
