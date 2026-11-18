package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.auto.inv.InvJob;
import com.jfant.specter.auto.inv.InvSync;
import com.jfant.specter.auto.inv.InvTiming;
import com.jfant.specter.auto.inv.Latency;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.notify.Notifications;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public class HumanTotem extends Module {
   private static final int TOTEM_POP = 35;
   private static final int MAX_ATTEMPTS = 3;
   private static final int NEAREST_CHOICES = 3;
   private static final long MS = 1000000L;
   private static final long EMERGENCY_NANOS = 3000000000L;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTiming = this.settings.createGroup("Timing");
   private final SettingGroup sgEmergency = this.settings.createGroup("Emergency");
   private final SettingGroup sgHotbar = this.settings.createGroup("Hotbar");
   private final Setting<HumanTotem.Method> method = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("method"))
                  .description(
                     "Open Inventory: opens your inventory, swaps the totem in, closes it again - what a real refill looks like to the server. Silent: swaps without opening anything."
                  ))
               .defaultValue(HumanTotem.Method.OpenInventory))
            .build()
      );
   private final Setting<Boolean> hotbarFirst = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hotbar-first")
            .description(
               "Take a totem from your hotbar before one from the rest of your inventory. Off: hotbar totems are only used when the rest of the inventory has none."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<HumanTotem.OffhandItem> offhandItem = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("offhand-item"))
                  .description(
                     "When something other than a totem is in your offhand. Only Empty: leave it, refill only an empty hand. Replace: swap a totem in anyway; the item goes to the slot the totem came from (works with a full inventory)."
                  ))
               .defaultValue(HumanTotem.OffhandItem.OnlyEmpty))
            .build()
      );
   private final Setting<Boolean> notifyEmpty = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("notify-empty")
            .description(
               "Show a notification when your offhand needs a totem and you have none left - once, and again only after you have had more and run out again."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> delay = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("refill-delay")
            .description(
               "How long it takes you to refill, 1-10. 1 is instant; 3 is about a twentieth of a second, 10 about a fifth, a little different every time."
            )
            .defaultValue(3)
            .range(1, 10)
            .sliderRange(1, 10)
            .build()
      );
   private final Setting<Integer> closeDelay = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("close-delay")
            .description("Open Inventory: milliseconds after the last click before the inventory closes again (a little different every time). 0 = right away.")
            .defaultValue(40)
            .range(0, 500)
            .sliderRange(0, 300)
            .visible(() -> this.method.get() == HumanTotem.Method.OpenInventory)
            .build()
      );
   private final Setting<Boolean> randomize = this.sgTiming
      .add(
         new BoolSetting.Builder()
            .name("randomize-timings")
            .description(
               "Draw every wait (noticing, picking, opening, clicking, closing) fresh around its setting, never the same twice in a row. Off: every wait is exactly its setting."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> variation = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("timing-variation")
            .description("How far each wait may stray from its setting, in percent. 25 is the classic Human Totem spread.")
            .defaultValue(25)
            .range(0, 60)
            .sliderRange(0, 60)
            .visible(this.randomize::get)
            .build()
      );
   private final Setting<HumanTotem.Emergency> emergency = this.sgEmergency
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("emergency"))
                  .description(
                     "When a refill is an emergency: its waits are squeezed into emergency-budget and it goes before any other module's inventory work. On Pop: for 3 seconds after a totem pops. Pop Or Low Health: also whenever your health is at or under emergency-health."
                  ))
               .defaultValue(HumanTotem.Emergency.Pop))
            .build()
      );
   private final Setting<Double> emergencyHealth = this.sgEmergency
      .add(
         new DoubleSetting.Builder()
            .name("emergency-health")
            .description("Health (with absorption, 20 = full) at or under which an empty offhand is an emergency.")
            .defaultValue(8.0)
            .range(1.0, 20.0)
            .sliderRange(1.0, 20.0)
            .visible(() -> this.emergency.get() == HumanTotem.Emergency.PopOrHealth)
            .build()
      );
   private final Setting<Integer> emergencyBudget = this.sgEmergency
      .add(
         new IntSetting.Builder()
            .name("emergency-budget")
            .description("Most milliseconds an emergency refill may spend waiting in all (noticing, opening, clicking). 0 = no waiting at all.")
            .defaultValue(120)
            .range(0, 400)
            .sliderRange(0, 300)
            .visible(() -> this.emergency.get() != HumanTotem.Emergency.Off)
            .build()
      );
   private final Setting<Boolean> keepHotbar = this.sgHotbar
      .add(
         new BoolSetting.Builder()
            .name("hotbar-totem")
            .description(
               "Also keep a totem in one of the hotbar slots ticked below, moved there from your inventory at the same pace once the offhand has one."
            )
            .defaultValue(false)
            .build()
      );
   private final List<Setting<Boolean>> hotbarSlots = new ArrayList<>();
   private final InvTiming timing;
   private final Object emptyKey;
   private final Object troubleKey;
   private HumanTotem.Phase phase;
   private HumanTotem.Goal goal;
   private long due;
   private int source;
   private double tempo;
   private int attempts;
   private int hotbarAttempts;
   private long hotbarWaitUntil;
   private int previousSource;
   private long emergencyUntil;
   private int jobToken;
   private boolean outNotified;
   private boolean lastFailed;

   public HumanTotem() {
      super(
         SpecterClientAddon.PVP,
         "human-totem",
         "Auto Totem",
         "Keeps a totem in your offhand: notices an empty or popped offhand, picks a totem and swaps it in at a human pace you set, checks the server took it, and hurries after a pop. Can also keep one in a hotbar slot.",
         new String[]{"auto-totem"}
      );

      for (int i = 1; i <= 9; i++) {
         this.hotbarSlots
            .add(
               this.sgHotbar
                  .add(
                     new BoolSetting.Builder()
                        .name("slot-" + i)
                        .description("Hotbar slot " + i + " may hold the hotbar totem.")
                        .defaultValue(i == 9)
                        .visible(this.keepHotbar::get)
                        .build()
                  )
            );
      }

      this.timing = new InvTiming();
      this.emptyKey = new Object();
      this.troubleKey = new Object();
      this.phase = HumanTotem.Phase.IDLE;
      this.goal = HumanTotem.Goal.OFFHAND;
      this.source = -1;
      this.tempo = 1.0;
      this.previousSource = -1;
   }

   @Override
   public void onActivate() {
      this.timing.reset();
      this.outNotified = false;
      this.reset();
   }

   @Override
   public void onDeactivate() {
      InvSync.cancel(this);
      this.reset();
   }

   private void reset() {
      this.cancelSchedule();
      this.phase = HumanTotem.Phase.IDLE;
      this.attempts = 0;
      this.hotbarAttempts = 0;
      this.hotbarWaitUntil = 0L;
      this.previousSource = -1;
      this.emergencyUntil = 0L;
      this.jobToken++;
   }

   public String statusText() {
      if (this.mc.player == null) {
         return "Ready";
      } else {
         return switch (this.phase) {
            case NOTICE, TARGET, SUBMITTED -> this.goal == HumanTotem.Goal.OFFHAND ? "Refilling" : "Hotbar";
            case RETRY -> "Retrying";
            case GAVE_UP -> "Gave up";
            default -> {
               if (this.needsOffhand() && !this.hasOffhandSource()) {
                  yield "No totems";
               } else {
                  ItemStack off = this.mc.player.getOffhandItem();
                  yield !off.isEmpty() && !off.is(Items.TOTEM_OF_UNDYING) ? "Offhand busy" : "Ready";
               }
            }
         };
      }
   }

   @Override
   public String getInfoString() {
      return this.isActive() && this.mc.player != null ? this.statusText() : null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      this.update();
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      this.update();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      InvSync.cancel(this);
      this.reset();
   }

   private void update() {
      if (this.mc.level != null && this.mc.player != null && this.mc.gameMode != null && this.mc.player.isAlive() && !(this.mc.player.getHealth() <= 0.0F)) {
         long now = System.nanoTime();
         this.timing.configure(this.randomize.get(), this.variation.get().intValue());
         this.watchSupply();
         if (this.phase != HumanTotem.Phase.SUBMITTED) {
            if (!this.canInteract()) {
               if (this.phase == HumanTotem.Phase.NOTICE || this.phase == HumanTotem.Phase.TARGET) {
                  this.cancelSchedule();
               }
            } else {
               if (this.phase == HumanTotem.Phase.RETRY) {
                  if (now < this.due) {
                     return;
                  }

                  this.phase = HumanTotem.Phase.IDLE;
               }

               if (this.phase == HumanTotem.Phase.GAVE_UP) {
                  if (this.needsOffhand()) {
                     return;
                  }

                  this.phase = HumanTotem.Phase.IDLE;
               }

               if (this.needsOffhand()) {
                  if (this.goal != HumanTotem.Goal.OFFHAND) {
                     this.cancelSchedule();
                  }

                  this.goal = HumanTotem.Goal.OFFHAND;
                  if (!this.hasOffhandSource()) {
                     this.cancelSchedule();
                  } else {
                     this.pace(now, this.emergencyNow(now));
                  }
               } else {
                  this.attempts = 0;
                  if (this.goal == HumanTotem.Goal.OFFHAND && this.phase != HumanTotem.Phase.IDLE) {
                     this.cancelSchedule();
                  }

                  this.goal = HumanTotem.Goal.HOTBAR;
                  if (!this.keepHotbar.get() || this.hotbarHasTotem()) {
                     this.hotbarAttempts = 0;
                  }

                  boolean wanted = this.keepHotbar.get() && this.hotbarTarget() >= 0 && this.storedSource(-1) >= 0;
                  if (wanted && this.hotbarAttempts < 3 && now >= this.hotbarWaitUntil) {
                     this.pace(now, false);
                  } else {
                     this.cancelSchedule();
                  }
               }
            }
         }
      } else {
         if (this.phase != HumanTotem.Phase.IDLE) {
            InvSync.cancel(this);
            this.reset();
         }
      }
   }

   private void pace(long now, boolean urgent) {
      if (now >= this.due) {
         int speed = Math.max(1, Math.min(10, this.delay.get()));
         double budget = urgent ? this.emergencyBudget.get().intValue() : -1.0;
         switch (this.phase) {
            case IDLE:
               if (this.findSource(this.goal) < 0) {
                  return;
               }

               this.tempo = this.timing.randomizing() ? 0.94 + ThreadLocalRandom.current().nextDouble() * 0.12 : 1.0;
               this.phase = HumanTotem.Phase.NOTICE;
               this.due = speed == 1 ? now : now + ms(this.timing.rangeMs(10 + (speed - 1) * 8, 20 + (speed - 1) * 13, this.tempo, share(budget, 0.35)));
               break;
            case NOTICE:
               this.source = this.findSource(this.goal);
               if (this.source < 0) {
                  this.cancelSchedule();
                  return;
               }

               this.phase = HumanTotem.Phase.TARGET;
               this.due = speed == 1 ? now : now + ms(this.timing.rangeMs(6.0 + (speed - 1) * 2.5, 12.0 + (speed - 1) * 4.5, this.tempo, share(budget, 0.15)));
               break;
            case TARGET:
               if (!this.validSource(this.source, this.goal)) {
                  this.phase = HumanTotem.Phase.NOTICE;
                  this.due = now + ms(this.timing.rangeMs(6.0, 14.0, this.tempo, share(budget, 0.1)));
                  return;
               }

               this.submit(now, urgent, speed, budget);
         }
      }
   }

   private void submit(long now, boolean urgent, int speed, double budget) {
      int token = ++this.jobToken;
      int from = this.source;
      HumanTotem.Goal what = this.goal;
      double openBase = speed == 1 ? 0.0 : 35.0 + 11.5 * speed;
      double stepBase = speed == 1 ? 0.0 : 24 + (speed - 1) * 14;
      InvJob.Builder job = InvJob.builder(this, what == HumanTotem.Goal.OFFHAND ? "totem to offhand" : "totem to hotbar")
         .openInventory(this.method.get() == HumanTotem.Method.OpenInventory)
         .pace(this.timing, openBase * this.tempo, stepBase * this.tempo, this.closeDelay.get().intValue(), share(budget, 0.3))
         .onDone(result -> this.onDone(result, token, what, from));
      if (what == HumanTotem.Goal.OFFHAND) {
         job.priority(urgent ? InvJob.Priority.EMERGENCY : InvJob.Priority.HIGH);
         job.offhandSwap(InvSync.menuSlot(from), () -> this.mc.player != null && this.needsOffhand() && this.validSource(from, HumanTotem.Goal.OFFHAND));
         if (this.keepHotbar.get() && this.method.get() == HumanTotem.Method.OpenInventory) {
            int hotbar = this.hotbarTarget();
            int spare = this.storedSource(from);
            if (hotbar >= 0 && spare >= 0 && hotbar != from) {
               job.hotbarSwap(
                  InvSync.menuSlot(spare),
                  hotbar,
                  () -> this.mc.player != null && isSource(this.mc.player.getInventory(), spare, false) && this.hotbarTarget() == hotbar
               );
            }
         }
      } else {
         int hotbar = this.hotbarTarget();
         if (hotbar < 0) {
            this.cancelSchedule();
            return;
         }

         job.priority(InvJob.Priority.NORMAL);
         job.hotbarSwap(
            InvSync.menuSlot(from),
            hotbar,
            () -> this.mc.player != null && isSource(this.mc.player.getInventory(), from, false) && this.hotbarTarget() == hotbar
         );
         this.hotbarAttempts++;
         this.hotbarWaitUntil = now + Math.min(5000L, Math.max(750L, Latency.rttMs() * 3L + 250L)) * 1000000L;
      }

      this.phase = HumanTotem.Phase.SUBMITTED;
      InvSync.submit(job.build());
   }

   private void onDone(InvJob.Result result, int token, HumanTotem.Goal what, int from) {
      if (token == this.jobToken && this.isActive() && this.phase == HumanTotem.Phase.SUBMITTED) {
         long now = System.nanoTime();
         this.cancelSchedule();
         InvJob.Outcome outcome = result.outcome();
         if (outcome.success()) {
            if (what == HumanTotem.Goal.OFFHAND) {
               this.attempts = 0;
               this.previousSource = from;
               if (this.lastFailed) {
                  Notifications.keyed(this.troubleKey, Notifications.Kind.SUCCESS, "Auto Totem", "Totem back in your offhand");
               }

               this.lastFailed = false;
            } else {
               this.hotbarAttempts = 0;
            }
         } else if (outcome.interrupted() || outcome == InvJob.Outcome.STALE) {
            this.due = now + ms(this.timing.rangeMs(40.0, 120.0, 1.0, -1.0));
         } else if (what != HumanTotem.Goal.HOTBAR) {
            this.attempts++;
            if (this.attempts >= 3) {
               this.phase = HumanTotem.Phase.GAVE_UP;
               this.lastFailed = true;
               Notifications.keyed(
                  this.troubleKey,
                  Notifications.Kind.WARNING,
                  "Auto Totem",
                  "The server refused the totem swap 3 times (" + result.detail() + "); trying again after the next change"
               );
            } else {
               long pause = Math.min(500L, Math.max(100L, (long)Latency.rttMs()));
               if (this.emergencyNow(now)) {
                  pause = Math.min(pause, (long)Math.max(0, this.emergencyBudget.get()));
               }

               this.phase = HumanTotem.Phase.RETRY;
               this.due = now + pause * 1000000L;
            }
         }
      }
   }

   private void cancelSchedule() {
      if (this.phase == HumanTotem.Phase.NOTICE || this.phase == HumanTotem.Phase.TARGET || this.phase == HumanTotem.Phase.SUBMITTED) {
         this.phase = HumanTotem.Phase.IDLE;
      }

      this.source = -1;
      this.due = 0L;
   }

   private static long ms(double millis) {
      return Math.round(millis * 1000000.0);
   }

   private static double share(double budget, double part) {
      return budget < 0.0 ? -1.0 : budget * part;
   }

   private boolean emergencyNow(long now) {
      return switch ((HumanTotem.Emergency)this.emergency.get()) {
         case Off -> false;
         case Pop -> now < this.emergencyUntil;
         case PopOrHealth -> now < this.emergencyUntil || this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount() <= this.emergencyHealth.get();
      };
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 35) {
         this.mc.execute(() -> {
            if (this.isActive() && this.mc.level != null && this.mc.player != null && packet.getEntity(this.mc.level) == this.mc.player) {
               this.onPop();
            }
         });
      }
   }

   private void onPop() {
      long now = System.nanoTime();
      if (this.emergency.get() != HumanTotem.Emergency.Off) {
         this.emergencyUntil = now + 3000000000L;
      }

      this.attempts = 0;
      if (this.phase == HumanTotem.Phase.GAVE_UP || this.phase == HumanTotem.Phase.RETRY) {
         this.phase = HumanTotem.Phase.IDLE;
      }

      if (this.goal == HumanTotem.Goal.HOTBAR && (this.phase == HumanTotem.Phase.NOTICE || this.phase == HumanTotem.Phase.TARGET)) {
         this.cancelSchedule();
      }

      if (this.goal == HumanTotem.Goal.HOTBAR && this.phase == HumanTotem.Phase.SUBMITTED) {
         this.jobToken++;
         this.phase = HumanTotem.Phase.IDLE;
         this.due = 0L;
      }
   }

   private void watchSupply() {
      boolean any = this.totemCount() > 0;
      if (any) {
         if (this.outNotified) {
            Notifications.keyed(this.emptyKey, Notifications.Kind.SUCCESS, "Auto Totem", "Totems back in your inventory");
         }

         this.outNotified = false;
      } else {
         if (!this.outNotified && this.needsOffhand() && this.notifyEmpty.get()) {
            Notifications.keyed(this.emptyKey, Notifications.Kind.ERROR, "Auto Totem", "No totems left");
            this.outNotified = true;
         }
      }
   }

   private boolean needsOffhand() {
      ItemStack off = this.mc.player.getOffhandItem();
      return off.isEmpty() ? true : !off.is(Items.TOTEM_OF_UNDYING) && this.offhandItem.get() == HumanTotem.OffhandItem.Replace;
   }

   private boolean canInteract() {
      return this.mc.gui.screen() != null && !(this.mc.gui.screen() instanceof InventoryScreen)
         ? false
         : this.mc.gameMode.getPlayerMode() == GameType.SURVIVAL
            && this.mc.player.containerMenu == this.mc.player.inventoryMenu
            && this.mc.player.inventoryMenu.getCarried().isEmpty();
   }

   private int totemCount() {
      Inventory inventory = this.mc.player.getInventory();
      int count = 0;

      for (int i = 0; i < 36; i++) {
         if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
            count++;
         }
      }

      return count;
   }

   private boolean hasOffhandSource() {
      return this.totemCount() > 0;
   }

   private int findSource(HumanTotem.Goal what) {
      Inventory inventory = this.mc.player.getInventory();
      ThreadLocalRandom random = ThreadLocalRandom.current();
      if (what == HumanTotem.Goal.HOTBAR) {
         return this.storedSource(-1);
      } else {
         List<Integer> hotbar = new ArrayList<>();

         for (int i = 0; i < 9; i++) {
            if (isSource(inventory, i, true)) {
               hotbar.add(i);
            }
         }

         if (this.hotbarFirst.get() && !hotbar.isEmpty()) {
            return hotbar.get(random.nextInt(hotbar.size()));
         } else {
            int stored = this.storedSource(-1);
            if (stored >= 0) {
               return stored;
            } else if (hotbar.isEmpty()) {
               return -1;
            } else {
               for (int ix : hotbar) {
                  if (!this.hotbarSlots.get(ix).get() || !this.keepHotbar.get()) {
                     return ix;
                  }
               }

               return hotbar.get(random.nextInt(hotbar.size()));
            }
         }
      }
   }

   private int storedSource(int except) {
      Inventory inventory = this.mc.player.getInventory();
      ThreadLocalRandom random = ThreadLocalRandom.current();
      List<Integer> stored = new ArrayList<>();

      for (int i = 9; i < 36; i++) {
         if (i != except && isSource(inventory, i, false)) {
            stored.add(i);
         }
      }

      if (stored.isEmpty()) {
         return -1;
      } else {
         int anchor = this.previousSource >= 9 && this.previousSource < 36 ? this.previousSource - 9 : random.nextInt(27);
         stored.sort(Comparator.comparingInt(index -> {
            int offset = index - 9;
            return Math.abs(offset % 9 - anchor % 9) + Math.abs(offset / 9 - anchor / 9);
         }));
         return stored.get(random.nextInt(Math.min(3, stored.size())));
      }
   }

   private boolean validSource(int index, HumanTotem.Goal what) {
      return isSource(this.mc.player.getInventory(), index, what == HumanTotem.Goal.OFFHAND);
   }

   private boolean hotbarHasTotem() {
      Inventory inventory = this.mc.player.getInventory();

      for (int i = 0; i < 9; i++) {
         if (this.hotbarSlots.get(i).get() && inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
            return true;
         }
      }

      return false;
   }

   private int hotbarTarget() {
      if (this.hotbarHasTotem()) {
         return -1;
      } else {
         Inventory inventory = this.mc.player.getInventory();
         int held = inventory.getSelectedSlot();
         int occupied = -1;

         for (int i = 0; i < 9; i++) {
            if (this.hotbarSlots.get(i).get()) {
               if (inventory.getItem(i).isEmpty()) {
                  return i;
               }

               if (occupied < 0 && i != held) {
                  occupied = i;
               }
            }
         }

         return occupied;
      }
   }

   private static boolean isSource(Inventory inventory, int index, boolean hotbar) {
      return index >= (hotbar ? 0 : 9) && index < 36 && inventory.getItem(index).is(Items.TOTEM_OF_UNDYING);
   }

   public static enum Emergency {
      Off("Off"),
      Pop("On Pop"),
      PopOrHealth("Pop Or Low Health");

      private final String title;

      private Emergency(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private static enum Goal {
      OFFHAND,
      HOTBAR;
   }

   public static enum Method {
      OpenInventory("Open Inventory"),
      Silent("Silent");

      private final String title;

      private Method(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum OffhandItem {
      OnlyEmpty("Only Empty"),
      Replace("Replace");

      private final String title;

      private OffhandItem(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private static enum Phase {
      IDLE,
      NOTICE,
      TARGET,
      SUBMITTED,
      RETRY,
      GAVE_UP;
   }
}
