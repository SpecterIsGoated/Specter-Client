package com.jfant.specter.macro;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public final class MacroThread {
   private static final long POLL_MS = 50L;
   private static final long CALL_TIMEOUT_SECONDS = 10L;
   private final String name;
   private volatile Thread thread;

   public MacroThread(String name) {
      this.name = name;
   }

   public synchronized void start(Runnable body, Consumer<String> halted, Consumer<Throwable> crashed) {
      this.stop();
      Thread worker = new Thread(() -> {
         try {
            body.run();
         } catch (MacroThread.Cancelled var22) {
         } catch (MacroThread.Halt var23) {
            if (this.thread == Thread.currentThread()) {
               halted.accept(var23.getMessage());
            }
         } catch (Throwable var24) {
            if (this.thread == Thread.currentThread()) {
               crashed.accept(var24);
            }
         } finally {
            synchronized (this) {
               if (this.thread == Thread.currentThread()) {
                  this.thread = null;
               }
            }
         }
      }, this.name);
      worker.setDaemon(true);
      this.thread = worker;
      worker.start();
   }

   public synchronized void stop() {
      Thread worker = this.thread;
      this.thread = null;
      if (worker != null && worker != Thread.currentThread()) {
         worker.interrupt();
      }
   }

   public boolean running() {
      return this.thread != null;
   }

   public void check() {
      if (this.thread != Thread.currentThread() || Thread.currentThread().isInterrupted()) {
         throw new MacroThread.Cancelled();
      }
   }

   public void sleep(long ms) {
      this.check();

      try {
         Thread.sleep(Math.max(0L, ms));
      } catch (InterruptedException var4) {
         Thread.currentThread().interrupt();
         throw new MacroThread.Cancelled();
      }

      this.check();
   }

   public <T> T call(Supplier<T> task) {
      this.check();
      CompletableFuture<T> future = Minecraft.getInstance().submit(task);

      try {
         return future.get(10L, TimeUnit.SECONDS);
      } catch (InterruptedException var6) {
         future.cancel(false);
         Thread.currentThread().interrupt();
         throw new MacroThread.Cancelled();
      } catch (ExecutionException var7) {
         Throwable cause = var7.getCause();
         if (cause instanceof RuntimeException runtime) {
            throw runtime;
         } else if (cause instanceof Error error) {
            throw error;
         } else {
            throw new IllegalStateException(cause);
         }
      } catch (TimeoutException var8) {
         future.cancel(false);
         throw new IllegalStateException("the game did not respond");
      }
   }

   public void run(Runnable task) {
      this.call(() -> {
         task.run();
         return Boolean.TRUE;
      });
   }

   public boolean waitFor(BooleanSupplier condition, long timeoutMs) {
      long end = System.currentTimeMillis() + timeoutMs;

      while (!this.call(condition::getAsBoolean)) {
         if (System.currentTimeMillis() >= end) {
            return false;
         }

         this.sleep(50L);
      }

      return true;
   }

   public static final class Cancelled extends RuntimeException {
      Cancelled() {
         super(null, null, false, false);
      }
   }

   public static final class Halt extends RuntimeException {
      public Halt(String message) {
         super(message, null, false, false);
      }
   }
}
