package com.jfant.specter.auto.inv;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.ContainerSlotUpdateEvent;
import com.jfant.specter.core.events.packets.InventoryEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.utils.player.InvUtils;
import com.jfant.specter.pvp.PvpLock;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class InvSync {
   public static final int OFFHAND_BUTTON = 40;
   public static final int OFFHAND_SLOT = 45;
   private static final long MS = 1000000L;
   private static final long JOB_LIMIT_MS = 4000L;
   private static final List<InvJob> queue = new ArrayList<>();
   private static final List<InvJob> awaiting = new ArrayList<>();
   private static final List<InvSync.Pending> inflight = new ArrayList<>();
   private static final List<Runnable> deliveries = new ArrayList<>();
   private static InvJob running;
   private static int stepIndex;
   private static long nextAt;
   private static long runningSince;
   private static long waitSince;
   private static boolean ourScreen;
   private static long closeAt;
   private static InvJob lastScreenJob;
   private static ClientLevel world;
   private static LocalPlayer player;
   private static boolean started;

   private InvSync() {
   }

   public static void init() {
      if (!started) {
         started = true;
         Latency.init();
         SpecterCore.EVENT_BUS.subscribe(new InvSync.Listener());
      }
   }

   public static void submit(InvJob job) {
      for (int i = queue.size() - 1; i >= 0; i--) {
         InvJob old = queue.get(i);
         if (old.owner == job.owner) {
            queue.remove(i);
            end(old, InvJob.Outcome.CANCELLED, -1, "replaced by a newer job");
         }
      }

      job.queuedAt = System.nanoTime();
      queue.add(job);
   }

   public static void cancel(Object owner) {
      queue.removeIf(job -> {
         if (job.owner != owner) {
            return false;
         } else {
            job.finished = true;
            return true;
         }
      });
      if (running != null && running.owner == owner) {
         running.finished = true;
         running = null;
      }

      awaiting.removeIf(job -> {
         if (job.owner != owner) {
            return false;
         } else {
            job.finished = true;
            return true;
         }
      });
   }

   public static boolean busy(Object owner) {
      if (running != null && running.owner == owner) {
         return true;
      } else {
         for (InvJob job : queue) {
            if (job.owner == owner) {
               return true;
            }
         }

         for (InvJob jobx : awaiting) {
            if (jobx.owner == owner) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean busyByOther(Object owner) {
      if (running != null && running.owner != owner) {
         return true;
      } else {
         for (InvJob job : queue) {
            if (job.owner != owner) {
               return true;
            }
         }

         for (InvJob jobx : awaiting) {
            if (jobx.owner != owner) {
               return true;
            }
         }

         for (InvSync.Pending pending : inflight) {
            if (pending.job.owner != owner) {
               return true;
            }
         }

         return ourScreen && lastScreenJob != null && lastScreenJob.owner != owner;
      }
   }

   public static boolean ourScreenOpen() {
      return ourScreen;
   }

   public static int menuSlot(int inventoryIndex) {
      return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
   }

   private static void update() {
      long now = System.nanoTime();
      LocalPlayer p = SpecterCore.mc.player;
      if (SpecterCore.mc.level != null && p != null && SpecterCore.mc.gameMode != null) {
         if (world != SpecterCore.mc.level) {
            if (world != null) {
               abortAll(InvJob.Outcome.ABORTED_WORLD, "changed world");
            }

            world = SpecterCore.mc.level;
            player = p;
         }

         if (player != p) {
            abortAll(InvJob.Outcome.ABORTED_DEATH, "respawned");
            player = p;
         }

         if (p.isAlive() && !p.isDeadOrDying()) {
            boolean inventoryOpen = SpecterCore.mc.screen instanceof InventoryScreen;
            if (ourScreen && !inventoryOpen) {
               ourScreen = false;
               closeAt = 0L;
               if (running != null && running.openInventory) {
                  abortRunning(InvJob.Outcome.ABORTED_SCREEN, "the inventory was closed");
               }
            }

            if (running != null && p.containerMenu != p.inventoryMenu) {
               abortRunning(InvJob.Outcome.ABORTED_SCREEN, "a container is open");
            }

            for (InvSync.Pending pending : new ArrayList<>(inflight)) {
               if (now >= pending.deadline) {
                  boolean ok = matches(p, pending);
                  settle(
                     pending,
                     ok ? InvJob.Outcome.CONFIRMED : InvJob.Outcome.MISMATCH,
                     ok ? "accepted" : "the inventory changed while the click was being confirmed"
                  );
               }
            }

            InvJob best = bestQueued();
            if (running != null && best != null && best.priority.ordinal() > running.priority.ordinal()) {
               abortRunning(InvJob.Outcome.PREEMPTED, "a more urgent job came first");
            }

            if (running == null && best != null) {
               queue.remove(best);
               start(best, now);
            }

            if (running != null) {
               step(now, p);
            }

            maybeClose(now, p);
            deliver();
         } else {
            if (hasWork()) {
               abortAll(InvJob.Outcome.ABORTED_DEATH, "died");
            }

            deliver();
         }
      } else {
         if (hasWork()) {
            abortAll(InvJob.Outcome.ABORTED_WORLD, "left the world");
         }

         world = null;
         player = null;
         deliver();
      }
   }

   private static void start(InvJob job, long now) {
      running = job;
      stepIndex = 0;
      runningSince = now;
      waitSince = 0L;
      boolean screenUp = SpecterCore.mc.screen instanceof InventoryScreen;
      nextAt = job.openInventory && screenUp ? now + job.timing.nextNanos(job.stepDelayMs, job.budgetMs) : now;
   }

   private static void step(long now, LocalPlayer p) {
      InvJob job = running;
      if (now - runningSince > (4000L + Latency.confirmWindowMs()) * 1000000L) {
         abortRunning(InvJob.Outcome.TIMEOUT, "could not finish in time");
      } else {
         boolean inventoryOpen = SpecterCore.mc.screen instanceof InventoryScreen;
         if (job.openInventory) {
            if (!inventoryOpen) {
               if (SpecterCore.mc.screen != null) {
                  abortRunning(InvJob.Outcome.ABORTED_SCREEN, "another screen is open");
                  return;
               }

               SpecterCore.mc.setScreen(new InventoryScreen(p));
               ourScreen = SpecterCore.mc.screen instanceof InventoryScreen;
               if (!ourScreen) {
                  abortRunning(InvJob.Outcome.ABORTED_SCREEN, "the inventory would not open");
                  return;
               }

               nextAt = Math.max(nextAt, now + job.timing.nextNanos(job.openDelayMs, job.budgetMs));
            }

            if (ourScreen) {
               closeAt = 0L;
            }
         } else if (SpecterCore.mc.screen != null && !inventoryOpen) {
            abortRunning(InvJob.Outcome.ABORTED_SCREEN, "a screen is open");
            return;
         }

         if (now >= nextAt) {
            InventoryMenu handler = p.inventoryMenu;
            if (p.containerMenu != handler) {
               abortRunning(InvJob.Outcome.ABORTED_SCREEN, "a container is open");
            } else {
               InvJob.Step step = job.steps.get(stepIndex);
               if (!handler.getCarried().isEmpty() && !job.cursorRecovery) {
                  if (waitSince == 0L) {
                     waitSince = now;
                  }

                  if (now - waitSince > Latency.confirmWindowMs() * 1000000L) {
                     abortRunning(InvJob.Outcome.CURSOR_BUSY, "something stayed on the cursor");
                  }
               } else if (step.guard != null && !step.guard.getAsBoolean()) {
                  worse(job, InvJob.Outcome.STALE, stepIndex, "no longer needed");
                  allSent(job, now);
               } else if (blocked(step)) {
                  if (waitSince == 0L) {
                     waitSince = now;
                  }
               } else if (!PvpLock.actedThisTick()) {
                  waitSince = 0L;
                  send(job, step, now, p, handler);
                  stepIndex++;
                  if (!job.stopSending && stepIndex < job.steps.size()) {
                     nextAt = now + job.timing.nextNanos(job.stepDelayMs, job.budgetMs);
                  } else {
                     allSent(job, now);
                  }
               }
            }
         }
      }
   }

   private static void allSent(InvJob job, long now) {
      job.allSent = true;
      running = null;
      if (job.openInventory && ourScreen) {
         lastScreenJob = job;
         closeAt = now + Math.max(1L, job.timing.nextNanos(job.closeDelayMs, job.budgetMs));
      }

      if (job.unresolved == 0) {
         end(job, job.worst, job.worstStep, job.worstDetail);
      } else if (!job.finished) {
         awaiting.add(job);
      }
   }

   private static void send(InvJob job, InvJob.Step step, long now, LocalPlayer p, InventoryMenu handler) {
      if (step.kind == InvJob.Kind.SELECT) {
         InvUtils.swap(step.slot, false);
         PvpLock.claimTick();
      } else {
         int size = handler.slots.size();
         ItemStack[] before = new ItemStack[size];

         for (int i = 0; i < size; i++) {
            before[i] = handler.getSlot(i).getItem().copy();
         }

         ItemStack cursorBefore = handler.getCarried().copy();
         SpecterCore.mc.gameMode.handleInventoryMouseClick(handler.containerId, step.slot, step.button, step.type, p);
         PvpLock.claimTick();
         List<Integer> changed = new ArrayList<>();

         for (int i = 0; i < size; i++) {
            if (!ItemStack.matches(before[i], handler.getSlot(i).getItem())) {
               changed.add(i);
            }
         }

         boolean cursorChanged = !ItemStack.matches(cursorBefore, handler.getCarried());
         if (changed.isEmpty() && !cursorChanged) {
            worse(job, InvJob.Outcome.NO_EFFECT, stepIndex, "the click changed nothing");
            job.stopSending = true;
         } else {
            int[] slots = new int[changed.size()];
            ItemStack[] expected = new ItemStack[changed.size()];

            for (int ix = 0; ix < slots.length; ix++) {
               slots[ix] = changed.get(ix);
               expected[ix] = handler.getSlot(slots[ix]).getItem().copy();
            }

            long deadline = now + Latency.confirmWindowMs() * 1000000L;
            inflight.add(new InvSync.Pending(job, stepIndex, slots, expected, handler.getCarried().copy(), now, deadline));
            job.unresolved++;
         }
      }
   }

   private static boolean blocked(InvJob.Step step) {
      if (inflight.isEmpty()) {
         return false;
      } else {
         int[] touches = step.touches();
         if (touches == null) {
            return true;
         } else {
            for (InvSync.Pending pending : inflight) {
               for (int slot : touches) {
                  if (pending.touches(slot)) {
                     return true;
                  }
               }
            }

            return false;
         }
      }
   }

   private static void maybeClose(long now, LocalPlayer p) {
      if (ourScreen && running == null && !queueWantsScreen()) {
         if (closeAt == 0L) {
            closeAt = now + 60000000L;
         }

         if (now >= closeAt) {
            if (SpecterCore.mc.screen instanceof InventoryScreen) {
               p.closeContainer();
            }

            ourScreen = false;
            closeAt = 0L;
         }
      }
   }

   private static boolean queueWantsScreen() {
      for (InvJob job : queue) {
         if (job.openInventory) {
            return true;
         }
      }

      return false;
   }

   private static InvJob bestQueued() {
      InvJob best = null;

      for (InvJob job : queue) {
         if (best == null || job.priority.ordinal() > best.priority.ordinal()) {
            best = job;
         }
      }

      return best;
   }

   private static boolean hasWork() {
      return running != null || !queue.isEmpty() || !awaiting.isEmpty() || !inflight.isEmpty() || ourScreen;
   }

   private static boolean matches(LocalPlayer p, InvSync.Pending pending) {
      InventoryMenu handler = p.inventoryMenu;

      for (int i = 0; i < pending.slots.length; i++) {
         if (!ItemStack.matches(handler.getSlot(pending.slots[i]).getItem(), pending.expected[i])) {
            return false;
         }
      }

      return ItemStack.matches(handler.getCarried(), pending.expectedCursor);
   }

   private static void onSlot(int slot) {
      LocalPlayer p = SpecterCore.mc.player;
      if (p != null && !inflight.isEmpty() && slot >= 0 && slot < p.inventoryMenu.slots.size()) {
         long now = System.nanoTime();
         ItemStack stack = p.inventoryMenu.getSlot(slot).getItem();

         for (InvSync.Pending pending : new ArrayList<>(inflight)) {
            for (int i = 0; i < pending.slots.length; i++) {
               if (pending.slots[i] == slot) {
                  Latency.sample((now - pending.sentAt) / 1000000.0);
                  if (!ItemStack.matches(stack, pending.expected[i])) {
                     settle(pending, InvJob.Outcome.MISMATCH, "the server put slot " + slot + " back");
                  } else if (!pending.agreed[i]) {
                     pending.agreed[i] = true;
                     if (++pending.agreedCount == pending.slots.length) {
                        settle(pending, InvJob.Outcome.CONFIRMED, "the server agreed");
                     }
                  }
                  break;
               }
            }
         }

         deliver();
      }
   }

   private static void onFullSync() {
      LocalPlayer p = SpecterCore.mc.player;
      if (p != null && !inflight.isEmpty()) {
         long now = System.nanoTime();

         for (InvSync.Pending pending : new ArrayList<>(inflight)) {
            Latency.sample((now - pending.sentAt) / 1000000.0);
            boolean ok = matches(p, pending);
            settle(pending, ok ? InvJob.Outcome.CONFIRMED : InvJob.Outcome.MISMATCH, ok ? "the server's resync agreed" : "the server's resync differs");
         }

         deliver();
      }
   }

   private static void settle(InvSync.Pending pending, InvJob.Outcome outcome, String detail) {
      if (inflight.remove(pending)) {
         InvJob job = pending.job;
         if (outcome == InvJob.Outcome.MISMATCH) {
            recoverCursor(pending);
         }

         if (!job.finished) {
            job.unresolved--;
            if (outcome != InvJob.Outcome.CONFIRMED) {
               worse(job, outcome, pending.step, detail);
            }

            if (job.allSent && job.unresolved == 0) {
               awaiting.remove(job);
               end(job, job.worst, job.worstStep, job.worstDetail);
            }
         }
      }
   }

   private static void recoverCursor(InvSync.Pending pending) {
      LocalPlayer p = SpecterCore.mc.player;
      if (p != null && pending.expectedCursor.isEmpty() && !p.inventoryMenu.getCarried().isEmpty()) {
         if (SpecterCore.mc.screen == null || SpecterCore.mc.screen instanceof InventoryScreen) {
            int free = -1;

            for (int slot = 9; slot <= 44 && free < 0; slot++) {
               if (p.inventoryMenu.getSlot(slot).getItem().isEmpty()) {
                  free = slot;
               }
            }

            if (free >= 0) {
               InvJob recovery = InvJob.builder(InvSync.class, "cursor recovery")
                  .priority(InvJob.Priority.EMERGENCY)
                  .openInventory(false)
                  .pace(new InvTiming().configure(false, 0.0), 0.0, 0.0, 0.0, 0.0)
                  .click(free, 0, ClickType.PICKUP, () -> SpecterCore.mc.player != null && !SpecterCore.mc.player.inventoryMenu.getCarried().isEmpty())
                  .build();
               recovery.cursorRecovery = true;
               submit(recovery);
            }
         }
      }
   }

   private static int severity(InvJob.Outcome outcome) {
      return switch (outcome) {
         case CONFIRMED -> 0;
         case STALE -> 1;
         case NO_EFFECT -> 2;
         case MISMATCH -> 3;
         default -> 4;
      };
   }

   private static void worse(InvJob job, InvJob.Outcome outcome, int step, String detail) {
      if (severity(outcome) > severity(job.worst)) {
         job.worst = outcome;
         job.worstStep = step;
         job.worstDetail = detail;
      }
   }

   private static void abortRunning(InvJob.Outcome outcome, String detail) {
      InvJob job = running;
      running = null;
      if (job != null) {
         end(job, outcome, stepIndex, detail);
      }
   }

   private static void abortAll(InvJob.Outcome outcome, String detail) {
      abortRunning(outcome, detail);

      for (InvJob job : new ArrayList<>(queue)) {
         end(job, outcome, -1, detail);
      }

      queue.clear();

      for (InvJob job : new ArrayList<>(awaiting)) {
         end(job, outcome, -1, detail);
      }

      awaiting.clear();
      inflight.clear();
      ourScreen = false;
      closeAt = 0L;
      lastScreenJob = null;
   }

   private static void end(InvJob job, InvJob.Outcome outcome, int step, String detail) {
      if (!job.finished) {
         job.finished = true;
         awaiting.remove(job);
         InvJob.Result result = new InvJob.Result(outcome, step, detail);
         deliveries.add(() -> job.callback.accept(result));
      }
   }

   private static void deliver() {
      while (!deliveries.isEmpty()) {
         List<Runnable> now = new ArrayList<>(deliveries);
         deliveries.clear();

         for (Runnable r : now) {
            try {
               r.run();
            } catch (RuntimeException var4) {
               SpecterCore.LOG.error("Inventory job callback failed", var4);
            }
         }
      }
   }

   private static final class Listener {
      @EventHandler(
         priority = 100
      )
      private void onTick(TickEvent.Pre event) {
         InvSync.update();
      }

      @EventHandler
      private void onFrame(Render3DEvent event) {
         InvSync.update();
      }

      @EventHandler
      private void onSlot(ContainerSlotUpdateEvent event) {
         if (event.packet.getContainerId() == 0) {
            InvSync.onSlot(event.packet.getSlot());
         }
      }

      @EventHandler
      private void onInventory(InventoryEvent event) {
         if (event.packet.containerId() == 0) {
            InvSync.onFullSync();
         }
      }

      @EventHandler
      private void onGameLeft(GameLeftEvent event) {
         InvSync.abortAll(InvJob.Outcome.ABORTED_WORLD, "left the world");
         InvSync.world = null;
         InvSync.player = null;
         InvSync.deliver();
      }
   }

   private static final class Pending {
      final InvJob job;
      final int step;
      final int[] slots;
      final ItemStack[] expected;
      final boolean[] agreed;
      final ItemStack expectedCursor;
      final long sentAt;
      final long deadline;
      int agreedCount;

      Pending(InvJob job, int step, int[] slots, ItemStack[] expected, ItemStack expectedCursor, long sentAt, long deadline) {
         this.job = job;
         this.step = step;
         this.slots = slots;
         this.expected = expected;
         this.agreed = new boolean[slots.length];
         this.expectedCursor = expectedCursor;
         this.sentAt = sentAt;
         this.deadline = deadline;
      }

      boolean touches(int slot) {
         for (int s : this.slots) {
            if (s == slot) {
               return true;
            }
         }

         return false;
      }
   }
}
