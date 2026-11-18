package com.jfant.specter.auto;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.auto.inv.InvJob;
import com.jfant.specter.auto.inv.InvSync;
import com.jfant.specter.auto.inv.InvTiming;
import com.jfant.specter.auto.inv.Latency;
import com.jfant.specter.build.BuildProfile;
import com.jfant.specter.build.HumanAim;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.notify.Notifications;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.PvpLock;
import com.jfant.specter.pvp.SlotReturn;
import java.util.Random;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.GameType;

public class AutoInvisibility extends Module {
   private static final long MS = 1000000L;
   private static final long AIM_LIMIT_NANOS = 1500000000L;
   private static final long FLIGHT_NANOS = 1500000000L;
   private static final int DRINK_LIMIT_TICKS = 60;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgInventory = this.settings.createGroup("Inventory");
   private final SettingGroup sgAim = this.settings.createGroup("Aim");
   private final SettingGroup sgPause = this.settings.createGroup("Pause");
   private final Setting<AutoInvisibility.Activation> activation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("activation"))
                  .description(
                     "Always: keep the effect up all the time. Player Nearby: only while another player is within player-range. On Key Press: one application each time you press the key below."
                  ))
               .defaultValue(AutoInvisibility.Activation.PlayerNearby))
            .build()
      );
   private final Setting<Double> playerRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("player-range")
            .description("Player Nearby: how close another player has to be, in blocks.")
            .defaultValue(24.0)
            .range(2.0, 128.0)
            .sliderRange(4.0, 64.0)
            .visible(() -> this.activation.get() == AutoInvisibility.Activation.PlayerNearby)
            .build()
      );
   private final Setting<Keybind> key = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("key")
            .description("On Key Press: the key that asks for a potion (ignored while the effect still has more than reapply-below left).")
            .defaultValue(Keybind.none())
            .visible(() -> this.activation.get() == AutoInvisibility.Activation.Keybind)
            .build()
      );
   private final Setting<Double> reapplyBelow = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("reapply-below")
            .description("Use the next potion once the effect has fewer than this many seconds left (0 = only once it has run out).")
            .defaultValue(5.0)
            .range(0.0, 120.0)
            .sliderRange(0.0, 60.0)
            .build()
      );
   private final Setting<AutoInvisibility.Kind> kind = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("potion-kind"))
                  .description("Which invisibility potions to use: thrown at your feet (Splash, Lingering, or either) or drunk."))
               .defaultValue(AutoInvisibility.Kind.Splash))
            .build()
      );
   private final Setting<Integer> maxTries = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-tries")
            .description("Potions to try in a row when the server does not give you the effect, before waiting retry-pause.")
            .defaultValue(2)
            .range(1, 5)
            .sliderRange(1, 5)
            .build()
      );
   private final Setting<Integer> retryPause = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("retry-pause")
            .description("Seconds to wait after max-tries potions failed before trying again.")
            .defaultValue(10)
            .range(1, 120)
            .sliderRange(1, 60)
            .build()
      );
   private final Setting<Integer> hotbarSlot = this.sgInventory
      .add(
         new IntSetting.Builder()
            .name("hotbar-slot")
            .description("The hotbar slot (1-9) a potion is brought to when none is in your hotbar already.")
            .defaultValue(9)
            .range(1, 9)
            .sliderRange(1, 9)
            .build()
      );
   private final Setting<Boolean> putBack = this.sgInventory
      .add(
         new BoolSetting.Builder()
            .name("put-back")
            .description("After a potion was brought to the hotbar, swap back so the item that was in that slot returns to it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<AutoInvisibility.Method> method = this.sgInventory
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("method"))
                  .description("Open Inventory: opens your inventory for the move and closes it after. Silent: moves without opening anything."))
               .defaultValue(AutoInvisibility.Method.OpenInventory))
            .build()
      );
   private final Setting<Integer> moveDelay = this.sgInventory
      .add(
         new IntSetting.Builder()
            .name("move-delay")
            .description("Milliseconds the inventory hand takes: from the inventory opening to the click, and from the click to it closing.")
            .defaultValue(90)
            .range(0, 500)
            .sliderRange(0, 300)
            .build()
      );
   private final Setting<Boolean> randomize = this.sgInventory
      .add(
         new BoolSetting.Builder()
            .name("randomize-timings")
            .description("Draw each inventory wait fresh around move-delay, never the same twice in a row.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> variation = this.sgInventory
      .add(
         new IntSetting.Builder()
            .name("timing-variation")
            .description("How far each inventory wait may stray from move-delay, in percent.")
            .defaultValue(30)
            .range(0, 60)
            .sliderRange(0, 60)
            .visible(this.randomize::get)
            .build()
      );
   private final Setting<Double> aimPitch = this.sgAim
      .add(
         new DoubleSetting.Builder()
            .name("aim-pitch")
            .description("How far down to look for the throw, in degrees (90 = straight down). A little jitter is added each time.")
            .defaultValue(85.0)
            .range(50.0, 90.0)
            .sliderRange(50.0, 90.0)
            .build()
      );
   private final Setting<Double> aimSpeed = this.sgAim
      .add(
         new DoubleSetting.Builder()
            .name("aim-speed")
            .description("How quickly the view turns down and back. 1 = a normal hand.")
            .defaultValue(1.2)
            .range(0.3, 3.0)
            .sliderRange(0.3, 3.0)
            .build()
      );
   private final Setting<Boolean> restoreView = this.sgAim
      .add(new BoolSetting.Builder().name("restore-view").description("Turn back to where you were looking after the throw.").defaultValue(true).build());
   private final Setting<Boolean> pauseUsing = this.sgPause
      .add(
         new BoolSetting.Builder()
            .name("pause-while-using")
            .description("Wait while you are eating, drinking, blocking or otherwise using an item.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> damagePause = this.sgPause
      .add(
         new DoubleSetting.Builder()
            .name("damage-pause")
            .description("Wait this many seconds after you hit something or were hurt (0 = off). Screens always pause it.")
            .defaultValue(0.0)
            .range(0.0, 10.0)
            .sliderRange(0.0, 5.0)
            .build()
      );
   private final InvTiming timing = new InvTiming();
   private final SlotReturn slots = new SlotReturn();
   private final Random random = new Random();
   private final Object missingKey = new Object();
   private HumanAim aim;
   private AutoInvisibility.State state = AutoInvisibility.State.IDLE;
   private long stateSince;
   private long retryAt;
   private int potionSlot = -1;
   private int movedFrom = -1;
   private int movedTo = -1;
   private int tries;
   private int jobToken;
   private int effectBefore;
   private long thrownAt;
   private boolean keyWasDown;
   private boolean keyRequest;
   private boolean missingNotified;
   private boolean confirmed;
   private float savedYaw;
   private float savedPitch;
   private boolean viewSaved;
   private boolean holdingUse;
   private String status = "Idle";
   private volatile long effectAt;
   private volatile int effectDuration;
   private volatile long hurtAt;
   private long hitAt;
   private volatile int playerId = -1;

   public AutoInvisibility() {
      super(
         SpecterClientAddon.PVP,
         "auto-invisibility",
         "Keeps you invisible: brings an invisibility potion to your hotbar, looks down and throws it, checks the server gave you the effect, and throws the next one before it runs out."
      );
   }

   @Override
   public void onActivate() {
      this.aim = new HumanAim(new BuildProfile.Aim(), this.random);
      this.timing.reset();
      this.tries = 0;
      this.retryAt = 0L;
      this.missingNotified = false;
      this.keyWasDown = true;
      this.keyRequest = false;
      this.enter(AutoInvisibility.State.IDLE, "Idle");
   }

   @Override
   public void onDeactivate() {
      InvSync.cancel(this);
      this.releaseUse();
      this.slots.restore(true);
      PvpLock.release(this);
      if (this.aim != null) {
         this.aim.stop();
      }

      this.viewSaved = false;
      this.state = AutoInvisibility.State.IDLE;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      InvSync.cancel(this);
      this.holdingUse = false;
      this.slots.forget();
      PvpLock.release(this);
      if (this.aim != null) {
         this.aim.stop();
      }

      this.viewSaved = false;
      this.movedFrom = -1;
      this.state = AutoInvisibility.State.IDLE;
   }

   @Override
   public String getInfoString() {
      return this.status;
   }

   @EventHandler
   private void onFrame(Render3DEvent event) {
      if (this.aim != null
         && this.mc.player != null
         && this.mc.screen == null
         && (this.state == AutoInvisibility.State.AIMING || this.state == AutoInvisibility.State.RESTORING)) {
         this.aim.frame(this.mc);
      }
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      this.hitAt = System.nanoTime();
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundUpdateMobEffectPacket packet
         && packet.getEntityId() == this.playerId
         && packet.getEffect().value() == MobEffects.INVISIBILITY.value()) {
         this.effectDuration = packet.getEffectDurationTicks();
         this.effectAt = System.nanoTime();
      } else if (event.packet instanceof ClientboundDamageEventPacket packet && packet.entityId() == this.playerId) {
         this.hurtAt = System.nanoTime();
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      LocalPlayer player = this.mc.player;
      if (player != null && this.mc.level != null && this.mc.gameMode != null) {
         if (!player.isAlive()) {
            if (this.state != AutoInvisibility.State.IDLE) {
               this.abandon("Died");
            }
         } else {
            this.playerId = player.getId();
            long now = System.nanoTime();
            this.timing.configure(this.randomize.get(), this.variation.get().intValue());
            if (this.aim != null) {
               this.aim.setSpeed(this.aimSpeed.get());
            }

            boolean keyDown = this.mc.screen == null && this.key.get().isSet() && this.key.get().isPressed();
            if (keyDown && !this.keyWasDown && this.activation.get() == AutoInvisibility.Activation.Keybind) {
               this.keyRequest = true;
            }

            this.keyWasDown = keyDown;
            switch (this.state) {
               case IDLE:
                  this.idle(player, now);
               case MOVING:
               case PUTTING_BACK:
               default:
                  break;
               case SELECTING:
                  this.selecting(player, now);
                  break;
               case AIMING:
                  this.aiming(player, now);
                  break;
               case USING:
                  this.using(player, now);
                  break;
               case CONFIRMING:
                  this.confirming(player, now);
                  break;
               case RESTORING:
                  this.restoring(player, now);
            }
         }
      }
   }

   private void idle(LocalPlayer player, long now) {
      int remaining = remainingTicks(player);
      if (now < this.retryAt) {
         this.status = "Waiting to retry";
      } else {
         boolean wanted = switch ((AutoInvisibility.Activation)this.activation.get()) {
            case Always -> true;
            case PlayerNearby -> this.playerNearby(player);
            case Keybind -> this.keyRequest;
         };
         if (!wanted) {
            this.status = remaining > 0 ? "Invisible " + seconds(remaining) : "Idle";
         } else if (remaining > this.reapplyBelow.get() * 20.0) {
            this.keyRequest = false;
            this.status = "Invisible " + seconds(remaining);
         } else {
            String pause = this.pauseReason(player, now);
            if (pause != null) {
               this.status = pause;
            } else {
               int hotbar = this.findPotion(true);
               int stored = hotbar >= 0 ? -1 : this.findPotion(false);
               if (hotbar < 0 && stored < 0) {
                  if (!this.missingNotified) {
                     Notifications.keyed(this.missingKey, Notifications.Kind.WARNING, "Auto Invisibility", "No " + this.kindWords() + " invisibility potions");
                     this.missingNotified = true;
                  }

                  this.keyRequest = false;
                  this.status = "No potions";
               } else {
                  if (this.missingNotified) {
                     Notifications.keyed(this.missingKey, Notifications.Kind.INFO, "Auto Invisibility", "Invisibility potions found");
                  }

                  this.missingNotified = false;
                  if (!InvSync.busyByOther(this) && !PvpLock.heldByOther(this)) {
                     this.keyRequest = false;
                     this.effectBefore = remaining;
                     if (hotbar >= 0) {
                        this.potionSlot = hotbar;
                        this.movedFrom = -1;
                        this.enter(AutoInvisibility.State.SELECTING, "Selecting");
                     } else {
                        int target = this.hotbarSlot.get() - 1;
                        int token = ++this.jobToken;
                        InvJob job = InvJob.builder(this, "invisibility potion to hotbar")
                           .priority(InvJob.Priority.LOW)
                           .openInventory(this.method.get() == AutoInvisibility.Method.OpenInventory)
                           .pace(this.timing, this.moveDelay.get().intValue(), this.moveDelay.get().intValue(), this.moveDelay.get().intValue() * 0.7, -1.0)
                           .hotbarSwap(
                              InvSync.menuSlot(stored), target, () -> this.mc.player != null && this.isPotion(this.mc.player.getInventory().getItem(stored))
                           )
                           .onDone(result -> this.onMoved(result, token, stored, target))
                           .build();
                        InvSync.submit(job);
                        this.enter(AutoInvisibility.State.MOVING, "Bringing a potion");
                     }
                  } else {
                     this.status = "Waiting for the hands";
                  }
               }
            }
         }
      }
   }

   private void onMoved(InvJob.Result result, int token, int from, int target) {
      if (token == this.jobToken && this.isActive() && this.state == AutoInvisibility.State.MOVING) {
         if (!result.outcome().success()) {
            this.fail("Could not bring a potion to the hotbar (" + result.detail() + ")", result.outcome().interrupted());
         } else {
            this.movedFrom = from;
            this.movedTo = target;
            this.potionSlot = target;
            this.enter(AutoInvisibility.State.SELECTING, "Selecting");
         }
      }
   }

   private void selecting(LocalPlayer player, long now) {
      if (!this.isPotion(player.getInventory().getItem(this.potionSlot))) {
         this.fail("The potion moved", true);
      } else if (!PvpLock.acquire(this)) {
         if (now - this.stateSince > 3000000000L) {
            this.fail("Another module kept the hotbar", true);
         }
      } else {
         if (!this.viewSaved) {
            this.savedYaw = player.getYRot();
            this.savedPitch = player.getXRot();
            this.viewSaved = true;
         }

         this.slots.take(this.potionSlot);
         if (this.kind.get() == AutoInvisibility.Kind.Drinkable) {
            this.enter(AutoInvisibility.State.USING, "Drinking");
         } else {
            double pitch = Math.min(90.0, this.aimPitch.get() + (this.random.nextDouble() - 0.5) * 4.0);
            this.aim.target(player, player.getEyePosition(), AutoInput.pointAlong(player, player.getYRot() + (this.random.nextDouble() - 0.5) * 6.0, pitch));
            this.enter(AutoInvisibility.State.AIMING, "Looking down");
         }
      }
   }

   private void aiming(LocalPlayer player, long now) {
      this.aim.tick(this.mc);
      if (this.mc.screen == null && this.slots.holding()) {
         boolean settled = this.aim.arrived(this.mc) || now - this.stateSince > 1500000000L;
         if (settled && !(player.getXRot() < this.aimPitch.get() - 15.0)) {
            if (!PvpLock.actedThisTick()) {
               ItemStack stack = player.getMainHandItem();
               if (this.isPotion(stack) && !Hands.coolingDown(stack)) {
                  this.thrownAt = System.nanoTime();
                  Hands.use(InteractionHand.MAIN_HAND);
                  this.confirmed = false;
                  this.enter(AutoInvisibility.State.CONFIRMING, "Confirming");
               } else {
                  this.fail("The potion is not in your hand", true);
               }
            }
         } else {
            if (now - this.stateSince > 3000000000L) {
               this.fail("Could not look down", true);
            }
         }
      } else {
         this.fail("Interrupted", true);
      }
   }

   private void using(LocalPlayer player, long now) {
      if (this.mc.screen != null || !this.slots.holding()) {
         this.fail("Interrupted", true);
      } else if (this.holdingUse) {
         AutoInput.hold(this.mc.options.keyUse, true);
         if (!player.isUsingItem() || player.getTicksUsingItem() >= 60) {
            this.releaseUse();
            this.confirmed = false;
            this.enter(AutoInvisibility.State.CONFIRMING, "Confirming");
         }
      } else if (!PvpLock.actedThisTick() && !player.isUsingItem()) {
         if (!this.isPotion(player.getMainHandItem())) {
            this.fail("The potion is not in your hand", true);
         } else {
            this.mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            PvpLock.claimTick();
            if (!player.isUsingItem()) {
               this.fail("The potion could not be drunk", false);
            } else {
               AutoInput.hold(this.mc.options.keyUse, true);
               this.holdingUse = true;
               this.thrownAt = System.nanoTime();
            }
         }
      }
   }

   private void confirming(LocalPlayer player, long now) {
      if (this.effectAt > this.thrownAt && this.effectDuration > this.effectBefore) {
         this.confirmed = true;
         this.tries = 0;
         Notifications.keyed(this, Notifications.Kind.SUCCESS, "Auto Invisibility", "Invisible for " + seconds(this.effectDuration));
         this.startRestore(player);
      } else {
         if (now - this.thrownAt > Latency.confirmWindowMs() * 1000000L + 1500000000L) {
            this.tries++;
            if (this.tries >= this.maxTries.get()) {
               Notifications.keyed(
                  this,
                  Notifications.Kind.WARNING,
                  "Auto Invisibility",
                  "No invisibility from " + this.tries + " potions; waiting " + this.retryPause.get() + " s"
               );
               this.retryAt = now + this.retryPause.get().intValue() * 1000L * 1000000L;
               this.tries = 0;
            } else {
               Notifications.keyed(this, Notifications.Kind.WARNING, "Auto Invisibility", "The server gave no invisibility; trying again");
            }

            this.startRestore(player);
         }
      }
   }

   private void startRestore(LocalPlayer player) {
      if (this.restoreView.get() && this.viewSaved && this.mc.screen == null) {
         this.aim.target(player, player.getEyePosition(), AutoInput.pointAlong(player, this.savedYaw, this.savedPitch));
         this.enter(AutoInvisibility.State.RESTORING, "Looking back");
      } else {
         this.finishThrow();
      }
   }

   private void restoring(LocalPlayer player, long now) {
      this.aim.tick(this.mc);
      if (this.aim.arrived(this.mc) || now - this.stateSince > 1500000000L || this.mc.screen != null) {
         this.finishThrow();
      }
   }

   private void finishThrow() {
      this.aim.stop();
      this.viewSaved = false;
      this.slots.restore(true);
      PvpLock.release(this);
      if (this.putBack.get() && this.movedFrom >= 0 && this.mc.player != null) {
         Inventory inventory = this.mc.player.getInventory();
         int from = this.movedFrom;
         int to = this.movedTo;
         this.movedFrom = -1;
         if (inventory.getItem(from).isEmpty() && inventory.getItem(to).isEmpty()) {
            this.enter(AutoInvisibility.State.IDLE, "Invisible");
         } else {
            int token = ++this.jobToken;
            InvJob job = InvJob.builder(this, "put the hotbar item back")
               .priority(InvJob.Priority.LOW)
               .openInventory(this.method.get() == AutoInvisibility.Method.OpenInventory)
               .pace(this.timing, this.moveDelay.get().intValue(), this.moveDelay.get().intValue(), this.moveDelay.get().intValue() * 0.7, -1.0)
               .hotbarSwap(InvSync.menuSlot(from), to, () -> this.mc.player != null && !this.isPotion(this.mc.player.getInventory().getItem(from)))
               .onDone(result -> {
                  if (token == this.jobToken && this.isActive() && this.state == AutoInvisibility.State.PUTTING_BACK) {
                     this.enter(AutoInvisibility.State.IDLE, this.confirmed ? "Invisible" : "Idle");
                  }
               })
               .build();
            InvSync.submit(job);
            this.enter(AutoInvisibility.State.PUTTING_BACK, "Putting back");
         }
      } else {
         this.movedFrom = -1;
         this.enter(AutoInvisibility.State.IDLE, this.confirmed ? "Invisible" : "Idle");
      }
   }

   private void fail(String why, boolean interrupted) {
      long now = System.nanoTime();
      if (!interrupted) {
         this.tries++;
         Notifications.keyed(this, Notifications.Kind.WARNING, "Auto Invisibility", why);
      }

      if (this.tries >= this.maxTries.get()) {
         this.retryAt = now + this.retryPause.get().intValue() * 1000L * 1000000L;
         this.tries = 0;
      } else {
         this.retryAt = now + (interrupted ? 500 : 1500) * 1000000L;
      }

      this.releaseUse();
      if (this.aim != null) {
         this.aim.stop();
      }

      this.slots.restore(true);
      PvpLock.release(this);
      this.viewSaved = false;
      this.movedFrom = -1;
      this.enter(AutoInvisibility.State.IDLE, why);
   }

   private void abandon(String why) {
      InvSync.cancel(this);
      this.releaseUse();
      if (this.aim != null) {
         this.aim.stop();
      }

      this.slots.forget();
      PvpLock.release(this);
      this.viewSaved = false;
      this.movedFrom = -1;
      this.enter(AutoInvisibility.State.IDLE, why);
   }

   private void releaseUse() {
      if (this.holdingUse) {
         this.holdingUse = false;
         AutoInput.restore(this.mc.options.keyUse);
      }
   }

   private void enter(AutoInvisibility.State next, String word) {
      this.state = next;
      this.stateSince = System.nanoTime();
      this.status = word;
   }

   private String pauseReason(LocalPlayer player, long now) {
      if (this.mc.screen != null) {
         return "Paused: screen open";
      } else if (player.isSpectator() || this.mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
         return "Paused: spectator";
      } else if (this.pauseUsing.get() && player.isUsingItem()) {
         return "Paused: using an item";
      } else if (AutoInput.physicallyHeld(this.mc.options.keyAttack) && PvpLock.actedThisTick()) {
         return "Paused: clicking";
      } else {
         double pause = this.damagePause.get();
         if (pause > 0.0) {
            long window = Math.round(pause * 1000.0) * 1000000L;
            if (now - this.hurtAt < window || now - this.hitAt < window) {
               return "Paused: fighting";
            }
         }

         return null;
      }
   }

   private boolean playerNearby(LocalPlayer player) {
      double range = this.playerRange.get();

      for (Player other : this.mc.level.players()) {
         if (other != player && !other.isSpectator() && other.isAlive() && other.distanceToSqr(player) <= range * range) {
            return true;
         }
      }

      return false;
   }

   private static int remainingTicks(LocalPlayer player) {
      MobEffectInstance effect = player.getEffect(MobEffects.INVISIBILITY);
      if (effect == null) {
         return 0;
      } else {
         return effect.isInfiniteDuration() ? 2147483647 : effect.getDuration();
      }
   }

   private int findPotion(boolean hotbar) {
      Inventory inventory = this.mc.player.getInventory();
      int from = hotbar ? 0 : 9;
      int to = hotbar ? 9 : 36;

      for (int i = from; i < to; i++) {
         if (this.isPotion(inventory.getItem(i))) {
            return i;
         }
      }

      return -1;
   }

   private boolean isPotion(ItemStack stack) {
      boolean rightKind = switch ((AutoInvisibility.Kind)this.kind.get()) {
         case Splash -> stack.is(Items.SPLASH_POTION);
         case Lingering -> stack.is(Items.LINGERING_POTION);
         case Drinkable -> stack.is(Items.POTION);
         case AnyThrowable -> stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
      };
      if (!rightKind) {
         return false;
      } else {
         PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
         if (contents == null) {
            return false;
         } else {
            for (MobEffectInstance effect : contents.getAllEffects()) {
               if (effect.getEffect().value() == MobEffects.INVISIBILITY.value()) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private String kindWords() {
      return switch ((AutoInvisibility.Kind)this.kind.get()) {
         case Splash -> "splash";
         case Lingering -> "lingering";
         case Drinkable -> "drinkable";
         case AnyThrowable -> "throwable";
      };
   }

   private static String seconds(int ticks) {
      if (ticks == 2147483647) {
         return "forever";
      } else {
         int total = ticks / 20;
         return total / 60 + ":" + String.format("%02d", total % 60);
      }
   }

   public static enum Activation {
      Always("Always"),
      PlayerNearby("Player Nearby"),
      Keybind("On Key Press");

      private final String title;

      private Activation(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum Kind {
      Splash("Splash"),
      Lingering("Lingering"),
      Drinkable("Drinkable"),
      AnyThrowable("Splash Or Lingering");

      private final String title;

      private Kind(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
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

   private static enum State {
      IDLE,
      MOVING,
      SELECTING,
      AIMING,
      USING,
      CONFIRMING,
      RESTORING,
      PUTTING_BACK;
   }
}
