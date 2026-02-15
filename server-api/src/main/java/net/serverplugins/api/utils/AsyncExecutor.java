package net.serverplugins.api.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.serverplugins.api.ServerAPI;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

public class AsyncExecutor {

    private static final BukkitScheduler SCHEDULER = Bukkit.getScheduler();

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, getAsyncExecutor());
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, getAsyncExecutor());
    }

    public static CompletableFuture<Void> runSync(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SCHEDULER.runTask(
                ServerAPI.getInstance(),
                () -> {
                    try {
                        runnable.run();
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
        return future;
    }

    public static <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        SCHEDULER.runTask(
                ServerAPI.getInstance(),
                () -> {
                    try {
                        future.complete(supplier.get());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
        return future;
    }

    public static CompletableFuture<Void> runAsyncLater(Runnable runnable, long delayTicks) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SCHEDULER.runTaskLaterAsynchronously(
                ServerAPI.getInstance(),
                () -> {
                    try {
                        runnable.run();
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                },
                delayTicks);
        return future;
    }

    public static CompletableFuture<Void> runSyncLater(Runnable runnable, long delayTicks) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SCHEDULER.runTaskLater(
                ServerAPI.getInstance(),
                () -> {
                    try {
                        runnable.run();
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                },
                delayTicks);
        return future;
    }

    @SafeVarargs
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... tasks) {
        return CompletableFuture.allOf(tasks);
    }

    private static Executor getAsyncExecutor() {
        return runnable -> SCHEDULER.runTaskAsynchronously(ServerAPI.getInstance(), runnable);
    }
}
