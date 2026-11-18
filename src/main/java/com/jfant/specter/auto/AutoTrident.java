package com.jfant.specter.auto;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.auto.inv.Latency;
import com.jfant.specter.build.BuildProfile;
import com.jfant.specter.build.HumanAim;
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
import com.jfant.specter.mixin.auto.LivingEntityFlagsAccessor;
import com.jfant.specter.notify.Notifications;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.PvpLock;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AutoTrident extends Module {
   private static final long MS = 1000000L;
   private static final int MIN_CHARGE = 10;
   private static final long AIM_LIMIT_NANOS = 700000000L;
   private static final long RETURN_LIMIT_NANOS = 12000000000L;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTiming = this.settings.createGroup("Timing");
   private final SettingGroup sgAim = this.settings.createGroup("Aim");
   private final SettingGroup sgStop = this.settings.createGroup("Stop");
   private final Setting<AutoTrident.Activation> activation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("activation"))
                  .description(
                     "While Held: whenever a trident is in your hand. Hold Key: only while you hold the key below. While Moving: only while you hold a movement key."
                  ))
               .defaultValue(AutoTrident.Activation.Held))
            .build()
      );
   private final Setting<Keybind> holdKey = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("hold-key")
            .description("The key to hold, with activation on Hold Key.")
            .defaultValue(Keybind.none())
            .visible(() -> this.activation.get() == AutoTrident.Activation.Key)
            .build()
      );
   private final Setting<AutoTrident.Wet> wet = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("wet-from"))
                  .description(
                     "Riptide only launches when you are wet. Water: only while touching water. Rain: only while rained on out of water. Water Or Rain: either."
                  ))
               .defaultValue(AutoTrident.Wet.Both))
            .build()
      );
   private final Setting<Boolean> throwMode = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("throw-mode")
            .description("Also throw tridents that have no Riptide, where you look. Off by default.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> loyaltyOnly = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("loyalty-only")
            .description("Throw mode: only throw a trident with Loyalty (it comes back; the next throw waits for it), so a trident is never left behind.")
            .defaultValue(true)
            .visible(this.throwMode::get)
            .build()
      );
   private final Setting<Integer> extraCharge = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("extra-charge")
            .description("Ticks held past the 10 a launch needs before letting go.")
            .defaultValue(1)
            .range(0, 40)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Integer> chargeJitter = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("charge-jitter")
            .description("Up to this many more ticks, drawn fresh each time, so no two charges are the same length.")
            .defaultValue(2)
            .range(0, 10)
            .sliderRange(0, 6)
            .build()
      );
   private final Setting<Integer> launchDelay = this.sgTiming
      .add(
         new IntSetting.Builder()
            .name("launch-delay")
            .description("Milliseconds to wait after a confirmed launch before charging again (a fifth either way).")
            .defaultValue(150)
            .range(0, 3000)
            .sliderRange(0, 1500)
            .build()
      );
   private final Setting<AutoTrident.PitchMode> pitchMode = this.sgAim
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("pitch-mode"))
                  .description(
                     "Keep Mine: launch wherever you look. Launch Pitch: turn your view to the pitch below first (smoothly, like a hand), keeping your direction."
                  ))
               .defaultValue(AutoTrident.PitchMode.Keep))
            .build()
      );
   private final Setting<Double> launchPitch = this.sgAim
      .add(
         new DoubleSetting.Builder()
            .name("launch-pitch")
            .description("Launch Pitch: the pitch to launch at. Negative looks up (-90 straight up), 0 level, positive down.")
            .defaultValue(-25.0)
            .range(-90.0, 90.0)
            .sliderRange(-90.0, 90.0)
            .visible(() -> this.pitchMode.get() == AutoTrident.PitchMode.Launch)
            .build()
      );
   private final Setting<Double> aimSpeed = this.sgAim
      .add(
         new DoubleSetting.Builder()
            .name("aim-speed")
            .description("Launch Pitch: how quickly the view turns. 1 = a normal hand.")
            .defaultValue(1.0)
            .range(0.3, 3.0)
            .sliderRange(0.3, 3.0)
            .visible(() -> this.pitchMode.get() == AutoTrident.PitchMode.Launch)
            .build()
      );
   private final Setting<Boolean> restorePitch = this.sgAim
      .add(
         new BoolSetting.Builder()
            .name("restore-pitch")
            .description("Launch Pitch: turn back to the pitch you had once launching stops.")
            .defaultValue(true)
            .visible(() -> this.pitchMode.get() == AutoTrident.PitchMode.Launch)
            .build()
      );
   private final Setting<Integer> minDurability = this.sgStop
      .add(
         new IntSetting.Builder()
            .name("min-durability")
            .description("Stop using a trident with this much durability left or less (a trident one use from breaking never launches anyway).")
            .defaultValue(10)
            .range(1, 250)
            .sliderRange(1, 100)
            .build()
      );
   private final Setting<Boolean> obstacleStop = this.sgStop
      .add(
         new BoolSetting.Builder()
            .name("stop-near-obstacle")
            .description("Riptide: do not launch when a block is closer than obstacle-distance in the direction of the launch.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> obstacleDistance = this.sgStop
      .add(
         new DoubleSetting.Builder()
            .name("obstacle-distance")
            .description("How far ahead must be clear of blocks for a launch, in blocks.")
            .defaultValue(3.0)
            .range(1.0, 10.0)
            .sliderRange(1.0, 8.0)
            .visible(this.obstacleStop::get)
            .build()
      );
   private final Setting<Integer> maxUnconfirmed = this.sgStop
      .add(
         new IntSetting.Builder()
            .name("max-unconfirmed")
            .description("Switch off after this many launches in a row the server never confirmed (something is refusing them).")
            .defaultValue(3)
            .range(1, 10)
            .sliderRange(1, 10)
            .build()
      );
   private final Random random = new Random();
   private HumanAim aim;
   private AutoTrident.State state = AutoTrident.State.IDLE;
   private boolean holdingUse;
   private InteractionHand hand;
   private int slot = -1;
   private int chargeTarget;
   private boolean riptideShot;
   private boolean thrownLoyal;
   private int damageBefore;
   private long stateSince;
   private long nextAt;
   private long releasedAt;
   private int unconfirmed;
   private double savedPitch = 0.0 / 0.0;
   private boolean restoring;
   private String status = "Idle";
   private volatile long serverRiptideAt;
   private volatile int playerId = -1;

   public AutoTrident() {
      super(
         SpecterClientAddon.TOOLS,
         "auto-trident",
         "Charges and releases your trident for you: Riptide launches whenever you are wet (water or rain), confirmed by the server; optionally throws Loyalty tridents."
      );
   }

   @Override
   public void onActivate() {
      this.aim = new HumanAim(new BuildProfile.Aim(), this.random);
      this.unconfirmed = 0;
      this.savedPitch = 0.0 / 0.0;
      this.restoring = false;
      this.enter(AutoTrident.State.IDLE, "Idle");
   }

   @Override
   public void onDeactivate() {
      this.releaseUse(true);
      if (this.aim != null) {
         this.aim.stop();
      }

      this.restoring = false;
      this.savedPitch = 0.0 / 0.0;
      this.state = AutoTrident.State.IDLE;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.holdingUse = false;
      if (this.aim != null) {
         this.aim.stop();
      }

      this.savedPitch = 0.0 / 0.0;
      this.restoring = false;
      this.state = AutoTrident.State.IDLE;
      PvpLock.release(this);
   }

   @Override
   public String getInfoString() {
      return this.status;
   }

   @EventHandler
   private void onFrame(Render3DEvent event) {
      if (this.aim != null
         && this.mc.player != null
         && this.mc.gui.screen() == null
         && (this.state == AutoTrident.State.AIMING || this.state == AutoTrident.State.CHARGING || this.restoring)) {
         this.aim.frame(this.mc);
      }
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundSetEntityDataPacket packet && packet.id() == this.playerId && packet.packedItems() != null) {
         int flagsId = LivingEntityFlagsAccessor.specter$livingFlags().id();

         for (DataValue<?> entry : packet.packedItems()) {
            if (entry.id() == flagsId && entry.value() instanceof Byte flags && (flags & 4) != 0) {
               this.serverRiptideAt = System.nanoTime();
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      LocalPlayer player = this.mc.player;
      if (player != null && this.mc.level != null && this.mc.gameMode != null && player.isAlive()) {
         this.playerId = player.getId();
         long now = System.nanoTime();
         if (this.aim != null) {
            this.aim.setSpeed(this.aimSpeed.get());
         }

         if (AutoInput.physicallyHeld(this.mc.options.keyUse)
            && !this.holdingUse
            && this.state != AutoTrident.State.CONFIRMING
            && this.state != AutoTrident.State.WAIT_RETURN) {
            if (this.state != AutoTrident.State.IDLE) {
               this.enter(AutoTrident.State.IDLE, "You are using it");
            }

            this.status = "Yours";
         } else {
            switch (this.state) {
               case IDLE:
                  this.idle(player, now);
                  break;
               case AIMING:
                  this.aiming(player, now);
                  break;
               case CHARGING:
                  this.charging(player, now);
                  break;
               case CONFIRMING:
                  this.confirming(player, now);
                  break;
               case WAIT_RETURN:
                  this.waitReturn(player, now);
                  break;
               case COOLDOWN:
                  if (!this.canRun(player, false)) {
                     this.endRun(player);
                     this.enter(AutoTrident.State.IDLE, "Idle");
                  } else if (now >= this.nextAt) {
                     this.enter(AutoTrident.State.IDLE, "Ready");
                  }
            }

            if (this.restoring && this.aim != null) {
               this.aim.tick(this.mc);
               if (this.aim.arrived(this.mc)) {
                  this.restoring = false;
                  this.aim.stop();
               }
            }
         }
      } else {
         this.releaseUse(false);
         this.state = AutoTrident.State.IDLE;
      }
   }

   private void idle(LocalPlayer player, long now) {
      if (!this.canRun(player, false)) {
         this.endRun(player);
         this.status = "Idle";
      } else {
         this.restoring = false;
         if (this.pitchMode.get() == AutoTrident.PitchMode.Launch) {
            if (Double.isNaN(this.savedPitch)) {
               this.savedPitch = player.getXRot();
            }

            this.aim.target(player, player.getEyePosition(), AutoInput.pointAlong(player, player.getYRot(), this.launchPitch.get()));
            this.enter(AutoTrident.State.AIMING, "Aiming");
         } else {
            this.startUse(player, now);
         }
      }
   }

   private void aiming(LocalPlayer player, long now) {
      if (!this.canRun(player, false)) {
         this.endRun(player);
         this.enter(AutoTrident.State.IDLE, "Idle");
      } else {
         this.aim.tick(this.mc);
         if (this.aim.arrived(this.mc) || now - this.stateSince > 700000000L) {
            this.startUse(player, now);
         }
      }
   }

   private void startUse(LocalPlayer player, long now) {
      InteractionHand use = tridentHand(player);
      if (use != null && !PvpLock.actedThisTick() && !PvpLock.heldByOther(this) && !player.isUsingItem()) {
         ItemStack stack = player.getItemInHand(use);
         this.riptideShot = isRiptide(player, stack);
         this.mc.gameMode.useItem(player, use);
         PvpLock.claimTick();
         if (player.isUsingItem() && player.getUsedItemHand() == use) {
            AutoInput.hold(this.mc.options.keyUse, true);
            this.holdingUse = true;
            this.hand = use;
            this.slot = player.getInventory().getSelectedSlot();
            this.chargeTarget = 10
               + this.extraCharge.get()
               + (this.chargeJitter.get() > 0 ? ThreadLocalRandom.current().nextInt(this.chargeJitter.get() + 1) : 0);
            this.enter(AutoTrident.State.CHARGING, "Charging");
         } else {
            this.enter(AutoTrident.State.COOLDOWN, "Refused");
            this.nextAt = now + 250000000L;
         }
      }
   }

   private void charging(LocalPlayer player, long now) {
      ItemStack held = this.hand == null ? ItemStack.EMPTY : player.getItemInHand(this.hand);
      boolean sameItem = held.is(Items.TRIDENT) && player.getInventory().getSelectedSlot() == this.slot;
      if (!sameItem || !player.isUsingItem()) {
         this.releaseUse(false);
         this.endRun(player);
         this.enter(AutoTrident.State.IDLE, "Stopped");
      } else if (AutoInput.physicallyHeld(this.mc.options.keyUse)) {
         this.holdingUse = false;
         this.endRun(player);
         this.enter(AutoTrident.State.IDLE, "Yours");
      } else {
         AutoInput.hold(this.mc.options.keyUse, true);
         if (this.pitchMode.get() == AutoTrident.PitchMode.Launch) {
            this.aim.tick(this.mc);
         }

         if (!this.canRun(player, false)) {
            this.releaseUse(true);
            this.endRun(player);
            this.enter(AutoTrident.State.IDLE, "Stopped");
         } else if (player.getTicksUsingItem() < this.chargeTarget) {
            this.status = "Charging " + player.getTicksUsingItem() + "/" + this.chargeTarget;
         } else if (this.canRun(player, true)) {
            this.damageBefore = held.getDamageValue();
            this.thrownLoyal = !this.riptideShot && hasLoyalty(held);
            this.releaseUse(true);
            this.releasedAt = now;
            boolean launchedHere = this.riptideShot && player.isAutoSpinAttack();
            if (this.riptideShot && !launchedHere) {
               this.enter(AutoTrident.State.COOLDOWN, "Missed the water");
               this.nextAt = now + 200000000L;
            } else {
               this.enter(AutoTrident.State.CONFIRMING, this.riptideShot ? "Launching" : "Throwing");
            }
         }
      }
   }

   private void confirming(LocalPlayer player, long now) {
      boolean confirmed;
      if (this.riptideShot) {
         ItemStack held = this.hand == null ? ItemStack.EMPTY : player.getItemInHand(this.hand);
         confirmed = this.serverRiptideAt > this.releasedAt || held.is(Items.TRIDENT) && held.getDamageValue() > this.damageBefore;
      } else {
         confirmed = this.hand == InteractionHand.OFF_HAND
            ? !player.getOffhandItem().is(Items.TRIDENT)
            : !player.getInventory().getItem(this.slot).is(Items.TRIDENT);
      }

      if (confirmed) {
         this.unconfirmed = 0;
         if (!this.riptideShot && this.thrownLoyal) {
            this.enter(AutoTrident.State.WAIT_RETURN, "Waiting for it");
         } else {
            this.cooldown(now, this.riptideShot ? "Launched" : "Thrown");
         }
      } else {
         if (now - this.releasedAt > Latency.confirmWindowMs() * 1000000L) {
            this.unconfirmed++;
            if (this.unconfirmed >= this.maxUnconfirmed.get()) {
               Notifications.keyed(
                  this, Notifications.Kind.ERROR, "Auto Trident", "The server confirmed none of the last " + this.unconfirmed + " launches; switched off"
               );
               this.toggle();
               return;
            }

            Notifications.keyed(
               this,
               Notifications.Kind.WARNING,
               "Auto Trident",
               "Launch not confirmed by the server (" + this.unconfirmed + "/" + this.maxUnconfirmed.get() + ")"
            );
            this.cooldown(now, "Unconfirmed");
         }
      }
   }

   private void waitReturn(LocalPlayer player, long now) {
      if (tridentHand(player) != null) {
         this.cooldown(now, "Caught");
      } else {
         if (now - this.stateSince > 12000000000L) {
            Notifications.keyed(this, Notifications.Kind.WARNING, "Auto Trident", "The trident did not come back to your hand");
            this.enter(AutoTrident.State.IDLE, "Lost it");
         }
      }
   }

   private void cooldown(long now, String word) {
      double ms = this.launchDelay.get().intValue() * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4);
      this.nextAt = now + Math.round(ms * 1000000.0);
      this.enter(AutoTrident.State.COOLDOWN, word);
   }

   private void releaseUse(boolean release) {
      LocalPlayer player = this.mc.player;
      if (this.holdingUse) {
         if (release && player != null && this.mc.gameMode != null && player.isUsingItem()) {
            this.mc.gameMode.releaseUsingItem(player);
         }

         AutoInput.restore(this.mc.options.keyUse);
      }

      this.holdingUse = false;
   }

   private void endRun(LocalPlayer player) {
      if (!Double.isNaN(this.savedPitch) && this.aim != null) {
         if (this.restorePitch.get() && this.pitchMode.get() == AutoTrident.PitchMode.Launch && this.mc.gui.screen() == null) {
            this.aim.target(player, player.getEyePosition(), AutoInput.pointAlong(player, player.getYRot(), this.savedPitch));
            this.restoring = true;
         } else {
            this.aim.stop();
         }

         this.savedPitch = 0.0 / 0.0;
      }
   }

   private void enter(AutoTrident.State next, String word) {
      this.state = next;
      this.stateSince = System.nanoTime();
      this.status = word;
   }

   private boolean canRun(LocalPlayer player, boolean atRelease) {
      if (this.mc.gui.screen() == null && !player.isSpectator()) {
         InteractionHand use = this.state == AutoTrident.State.CHARGING && this.hand != null ? this.hand : tridentHand(player);
         if (use == null) {
            return false;
         } else {
            ItemStack stack = player.getItemInHand(use);
            if (stack.nextDamageWillBreak()) {
               return false;
            } else if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= this.minDurability.get()) {
               return false;
            } else if (Hands.coolingDown(stack)) {
               return false;
            } else if (!this.activated()) {
               return false;
            } else {
               boolean riptide = isRiptide(player, stack);
               if (!riptide) {
                  return this.throwMode.get() && (!this.loyaltyOnly.get() || hasLoyalty(stack));
               } else {
                  return !this.wetEnough(player) ? false : !atRelease || !this.obstacleStop.get() || !this.obstacleAhead(player);
               }
            }
         }
      } else {
         return false;
      }
   }

   private boolean activated() {
      return switch ((AutoTrident.Activation)this.activation.get()) {
         case Held -> true;
         case Key -> this.holdKey.get().isSet() && this.holdKey.get().isPressed();
         case Moving -> AutoInput.physicallyHeld(this.mc.options.keyUp)
            || AutoInput.physicallyHeld(this.mc.options.keyDown)
            || AutoInput.physicallyHeld(this.mc.options.keyLeft)
            || AutoInput.physicallyHeld(this.mc.options.keyRight);
      };
   }

   private boolean wetEnough(LocalPlayer player) {
      boolean water = player.isInWater();
      boolean rain = !water && player.isInWaterOrRain();

      return switch ((AutoTrident.Wet)this.wet.get()) {
         case Water -> water;
         case Rain -> rain;
         case Both -> water || rain;
      };
   }

   private boolean obstacleAhead(LocalPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = AutoInput.pointAlong(player, player.getYRot(), player.getXRot()).subtract(eye).normalize().scale(this.obstacleDistance.get()).add(eye);
      HitResult hit = this.mc.level.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, player));
      return hit.getType() == Type.BLOCK;
   }

   private static InteractionHand tridentHand(LocalPlayer player) {
      if (player.getMainHandItem().is(Items.TRIDENT)) {
         return InteractionHand.MAIN_HAND;
      } else {
         return player.getOffhandItem().is(Items.TRIDENT) && Hands.passesClick(player.getMainHandItem()) ? InteractionHand.OFF_HAND : null;
      }
   }

   private static boolean isRiptide(LocalPlayer player, ItemStack stack) {
      return stack.is(Items.TRIDENT) && EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0.0F;
   }

   private static boolean hasLoyalty(ItemStack stack) {
      for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
         if (enchantment.is(Enchantments.LOYALTY)) {
            return true;
         }
      }

      return false;
   }

   public static enum Activation {
      Held("While Held"),
      Key("Hold Key"),
      Moving("While Moving");

      private final String title;

      private Activation(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum PitchMode {
      Keep("Keep Mine"),
      Launch("Launch Pitch");

      private final String title;

      private PitchMode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private static enum State {
      IDLE,
      AIMING,
      CHARGING,
      CONFIRMING,
      WAIT_RETURN,
      COOLDOWN;
   }

   public static enum Wet {
      Water("Water"),
      Rain("Rain"),
      Both("Water Or Rain");

      private final String title;

      private Wet(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
