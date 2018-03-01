package com.github.mcollovati.vaadin;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.ui.UI;

public class StopJobTest {

    public static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(5);

    public void updateProgressBar() {
        System.out.println("REport compltetd. YAHOOOO!!!");
    }

    public void doAll(List<Report> reports) {
        for (Report report : reports) {
            runReport(report, 1500)
                .thenRun(this::updateProgressBar)
                .exceptionally( ex -> {
                    System.out.println("Got Error " + ex.getMessage());
                    return null;
                });
            ;
        }
    }

    public static CompletableFuture<?> runReport(Report report, long timeoutInMillis) {
        CompletableFuture<?> reportJob = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                report.generateReport();
            } catch (Exception e) {
                reportJob.completeExceptionally(e);
            }
            reportJob.complete(null);
        });
        t.start();
        try {
            reportJob.get(timeoutInMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            t.stop();
            reportJob.completeExceptionally(e);
        }
        return reportJob;
    }

    public static void main(String[] args) throws Exception {
        new StopJobTest().doAll(Arrays.asList(new Report(), new Report(100000)));
        /*
        CompletableFuture<?> reportJob = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                new Task().call();
            } catch (Exception e) {
                reportJob.completeExceptionally(e);
            }
            reportJob.complete(null);
        });
        t.start();
        try {
            reportJob.get(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            t.stop();
        }
        */
        System.out.println("=== BEFORE sleep");
        Thread.sleep(10000);
        System.out.println("=== AFTER sleep");
        //executor.shutdownNow();
        SCHEDULED_EXECUTOR_SERVICE.shutdownNow();
        System.out.println("=== AFTER shutdown");

    }

    public static CompletableFuture<?> limitExecutionTime(long timeoutMillis, Callable<?> op) {
        UI currentUI = UI.getCurrent();
        CompletableFuture<?> job = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            //Future<?> f = SCHEDULED_EXECUTOR_SERVICE.submit(op);

            CompletableFuture f = new CompletableFuture<>();
            TaskRunner.run(op, f);
            SCHEDULED_EXECUTOR_SERVICE.schedule(() -> f.cancel(true), timeoutMillis, TimeUnit.MILLISECONDS);
            try {
                f.get();
                System.out.println("== limitExecutionTime::completed");
                job.complete(null);
            } catch (InterruptedException | ExecutionException | CancellationException e) {
                System.out.println("== limitExecutionTime::timeout");
                job.completeExceptionally(e);
            }
        });
        return job.whenComplete((unused, throwable) -> currentUI.access(() -> {
                if (throwable != null) {
                    System.out.println("Execution took beyond the maximum allowed time.");
                } else {
                    // So something with UI when computation has ended
                    System.out.println("Job completed.");
                }
            })
        );
    }

    public static void main2(String[] args) throws Exception {
        //ExecutorService executor = Executors.newSingleThreadExecutor();
        Task task = new Task();
        //Future<String> future = executor.submit(task);
        CompletableFuture result = new CompletableFuture();

        limitExecutionTime(1500, task);
                  /*
            try
            {
                System.out.println("Started..");
                System.out.println(future.get(50, TimeUnit.MILLISECONDS));
                System.out.println("Finished!");
            } catch (TimeoutException e) {
                future.cancel(true);
                System.out.println("Terminated!");
            }
            */

        System.out.println("=== BEFORE sleep");
        Thread.sleep(10000);
        System.out.println("=== AFTER sleep");
        //executor.shutdownNow();
        SCHEDULED_EXECUTOR_SERVICE.shutdownNow();
        System.out.println("=== AFTER shutdown");
    }

    static class Report {
        private static final AtomicInteger counter = new AtomicInteger();
        private final int duration;
        private final int id;

        public Report(int duration) {
            this.duration = duration;
            this.id = counter.incrementAndGet();
        }

        public Report() {
            this(1000000000);
        }

        void generateReport() {

            long startTime = System.nanoTime();
            log("Start task");
            // Just to demo a long running task of 4 seconds.
            for (int x = 0; x < duration; x++) {
                ThreadLocalRandom.current().nextInt();
                if (x % (duration / 10) == 0) {
                    log("Running " + x + " of " + duration + " for report ");
                    throw new RuntimeException("BOO!");
                }
                //if (Thread.currentThread().isInterrupted()) {
                //throw new RuntimeException("Stop me!");
                //}
            }

            log("Task Completed in " + (Duration.ofNanos(System.nanoTime() - startTime).toMillis()) + " ms");
        }

        void log(String message) {
            System.out.printf("Report [%d]::%s\n", id, message);
        }
    }

    static class TaskRunner<T> {

        public static <T> CompletableFuture run(Callable<T> op, CompletableFuture<T> future) {

            MyThread t = new MyThread(() -> {
                try {
                    future.complete(op.call());
                } catch (Exception e) {
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }
            });
            //t.setDaemon(true);
            t.setUncaughtExceptionHandler((t1, e) -> {
                System.out.println("Got Uncaught Exception");
                future.completeExceptionally(e);
            });
            t.start();
            return future.whenComplete((unused, err) -> {
                System.out.println("Try to interrup thread " + err);
                if (t.isAlive() && !t.isInterrupted()) {
                    System.out.println("interrupting");
                    t.stop();
                }
            });
        }

        private static class MyThread extends Thread {

            public MyThread(Runnable target) {
                super(target);
            }

            public void forceInterrupt() {
                Thread.currentThread();
            }
        }
    }

    static class Task implements Callable<String> {
        @Override
        public String call() throws Exception {
            System.out.println("Start task");
            // Just to demo a long running task of 4 seconds.
            for (int x = 0; x < 100000000; x++) {
                new Random().nextInt();
                if (x % 10000000 == 0) {
                    System.out.println(x);
                }
                //if (Thread.currentThread().isInterrupted()) {
                //throw new RuntimeException("Stop me!");
                //}
            }
            System.out.println("Task Completed");
            return "Task completed!";
        }
    }
}
