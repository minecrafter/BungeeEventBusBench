/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package io.minimum.minecraft.tobench;

import io.minimum.minecraft.tobench.impls.COWEventBus;
import io.minimum.minecraft.tobench.impls.LockingEventBus;
import io.minimum.minecraft.tobench.impls.CHMEventBus;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class MyBenchmark {

    private LockingEventBus lockingEventBus = new LockingEventBus();
    private CHMEventBus chmEventBus = new CHMEventBus();
    private COWEventBus cowEventBus = new COWEventBus();

    @Param({"1", "2", "4", "8", "16"})
    private int registeredHandlers;

    @Setup
    public void setup(Blackhole blackhole) {
        for (int i = 0; i < registeredHandlers; i++) {
            chmEventBus.register(new TestEventHandler(blackhole));
            lockingEventBus.register(new TestEventHandler(blackhole));
            cowEventBus.register(new TestEventHandler(blackhole));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void chmEventBusThrpt() {
        // place your benchmarked code here
        chmEventBus.post(TestEvent.EVENT);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void lockingEventBusThrpt() {
        // place your benchmarked code here
        lockingEventBus.post(TestEvent.EVENT);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void cowEventBusThrpt() {
        // place your benchmarked code here
        cowEventBus.post(TestEvent.EVENT);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MyBenchmark.class.getSimpleName())
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
                .threads(4)
                .build();
        new Runner(opt).run();
    }

    @State(Scope.Benchmark)
    public static class TestEvent {
        private static final TestEvent EVENT = new TestEvent();
    }

    @State(Scope.Benchmark)
    public static class TestEventHandler {
        private final Blackhole blackhole;

        public TestEventHandler(Blackhole blackhole) {
            this.blackhole = blackhole;
        }

        @EventHandler
        public void test(TestEvent event) {
            blackhole.consume(event);
        }
    }
}
