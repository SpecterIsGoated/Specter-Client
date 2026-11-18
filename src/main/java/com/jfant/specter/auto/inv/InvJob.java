package com.jfant.specter.auto.inv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.world.inventory.ClickType;

public final class InvJob {
   final Object owner;
   final String label;
   final InvJob.Priority priority;
   final List<InvJob.Step> steps;
   final boolean openInventory;
   final InvTiming timing;
   final double openDelayMs;
   final double stepDelayMs;
   final double closeDelayMs;
   final double budgetMs;
   final Consumer<InvJob.Result> callback;
   int unresolved;
   boolean allSent;
   boolean finished;
   boolean stopSending;
   boolean cursorRecovery;
   InvJob.Outcome worst = InvJob.Outcome.CONFIRMED;
   int worstStep = -1;
   String worstDetail = "";
   long queuedAt;

   private InvJob(InvJob.Builder b) {
      this.owner = b.owner;
      this.label = b.label;
      this.priority = b.priority;
      this.steps = Collections.unmodifiableList(new ArrayList<>(b.steps));
      this.openInventory = b.openInventory;
      this.timing = b.timing;
      this.openDelayMs = b.openDelayMs;
      this.stepDelayMs = b.stepDelayMs;
      this.closeDelayMs = b.closeDelayMs;
      this.budgetMs = b.budgetMs;
      this.callback = b.callback;
   }

   public Object owner() {
      return this.owner;
   }

   public InvJob.Priority priority() {
      return this.priority;
   }

   public String label() {
      return this.label;
   }

   public static InvJob.Builder builder(Object owner, String label) {
      return new InvJob.Builder(owner, label);
   }

   public static final class Builder {
      private final Object owner;
      private final String label;
      private InvJob.Priority priority = InvJob.Priority.NORMAL;
      private final List<InvJob.Step> steps = new ArrayList<>();
      private boolean openInventory;
      private InvTiming timing = new InvTiming();
      private double openDelayMs = 60.0;
      private double stepDelayMs = 90.0;
      private double closeDelayMs = 60.0;
      private double budgetMs = -1.0;
      private Consumer<InvJob.Result> callback = result -> {};

      private Builder(Object owner, String label) {
         this.owner = owner;
         this.label = label;
      }

      public InvJob.Builder priority(InvJob.Priority priority) {
         this.priority = priority;
         return this;
      }

      public InvJob.Builder openInventory(boolean open) {
         this.openInventory = open;
         return this;
      }

      public InvJob.Builder pace(InvTiming timing, double openDelayMs, double stepDelayMs, double closeDelayMs, double budgetMs) {
         this.timing = timing;
         this.openDelayMs = openDelayMs;
         this.stepDelayMs = stepDelayMs;
         this.closeDelayMs = closeDelayMs;
         this.budgetMs = budgetMs;
         return this;
      }

      public InvJob.Builder click(int slot, int button, ClickType type, BooleanSupplier guard) {
         this.steps.add(new InvJob.Step(InvJob.Kind.CLICK, slot, button, type, guard));
         return this;
      }

      public InvJob.Builder hotbarSwap(int slot, int hotbar, BooleanSupplier guard) {
         return this.click(slot, hotbar, ClickType.SWAP, guard);
      }

      public InvJob.Builder offhandSwap(int slot, BooleanSupplier guard) {
         return this.click(slot, 40, ClickType.SWAP, guard);
      }

      public InvJob.Builder throwStack(int slot, BooleanSupplier guard) {
         return this.click(slot, 1, ClickType.THROW, guard);
      }

      public InvJob.Builder select(int hotbar, BooleanSupplier guard) {
         this.steps.add(new InvJob.Step(InvJob.Kind.SELECT, hotbar, 0, null, guard));
         return this;
      }

      public InvJob.Builder onDone(Consumer<InvJob.Result> callback) {
         this.callback = callback == null ? result -> {} : callback;
         return this;
      }

      public InvJob build() {
         if (this.steps.isEmpty()) {
            throw new IllegalStateException("An inventory job needs at least one step");
         } else {
            return new InvJob(this);
         }
      }
   }

   static enum Kind {
      CLICK,
      SELECT;
   }

   public static enum Outcome {
      CONFIRMED,
      MISMATCH,
      NO_EFFECT,
      STALE,
      PREEMPTED,
      CANCELLED,
      ABORTED_SCREEN,
      ABORTED_DEATH,
      ABORTED_WORLD,
      CURSOR_BUSY,
      TIMEOUT;

      public boolean success() {
         return this == CONFIRMED;
      }

      public boolean interrupted() {
         return this == PREEMPTED || this == CANCELLED || this == ABORTED_SCREEN || this == ABORTED_DEATH || this == ABORTED_WORLD || this == CURSOR_BUSY;
      }
   }

   public static enum Priority {
      LOW,
      NORMAL,
      HIGH,
      EMERGENCY;
   }

   public record Result(InvJob.Outcome outcome, int step, String detail) {
   }

   static final class Step {
      final InvJob.Kind kind;
      final int slot;
      final int button;
      final ClickType type;
      final BooleanSupplier guard;

      Step(InvJob.Kind kind, int slot, int button, ClickType type, BooleanSupplier guard) {
         this.kind = kind;
         this.slot = slot;
         this.button = button;
         this.type = type;
         this.guard = guard;
      }

      int[] touches() {
         if (this.kind == InvJob.Kind.CLICK && this.type == ClickType.SWAP) {
            int other = this.button == 40 ? 45 : (this.button >= 0 && this.button < 9 ? 36 + this.button : -1);
            return other < 0 ? null : new int[]{this.slot, other};
         } else {
            return this.kind == InvJob.Kind.CLICK && this.type == ClickType.THROW ? new int[]{this.slot} : null;
         }
      }
   }
}
