package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.pvp.Blast;
import com.jfant.specter.pvp.BlastMath;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.HoldKey;
import com.jfant.specter.pvp.Pace;
import com.jfant.specter.pvp.Placing;
import com.jfant.specter.pvp.PvpLock;
import com.jfant.specter.pvp.SlotReturn;
import com.jfant.specter.pvp.Targets;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public class AnchorPvp extends Module {
   private static final double TARGET_RANGE = 10.0;
   private static final int COUNTER_CAP = 1000;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final HoldKey hold = new HoldKey(this.sgGeneral, "it places, charges and sets off anchors");
   private final Setting<Integer> placeDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("place-delay")
            .description("Ticks to wait after the last click before placing an anchor.")
            .defaultValue(1)
            .range(0, 10)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Integer> chargeDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("charge-delay")
            .description("Ticks to wait after the last click before charging with glowstone.")
            .defaultValue(1)
            .range(0, 10)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Integer> explodeDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("explode-delay")
            .description("Ticks to wait after the last click before setting the anchor off.")
            .defaultValue(1)
            .range(0, 10)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Integer> spread = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("random-spread")
            .description("Up to this many extra ticks on each delay, drawn fresh every click. 0 = exact.")
            .defaultValue(1)
            .range(0, 4)
            .sliderRange(0, 4)
            .build()
      );
   private final Setting<Boolean> place = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("place")
            .description("Put an anchor against the block you look at. Off: only charge and set off anchors already there.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> switchBack = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("switch-back")
            .description("When you let go, go back to the hotbar slot you were on - unless you scrolled away yourself.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> maxSelfDamage = this.sgSafety
      .add(
         new DoubleSetting.Builder()
            .name("max-self-damage")
            .description("Never charge or set off an anchor whose blast would do you more than this (2 = one heart).")
            .defaultValue(20.0)
            .range(0.0, 36.0)
            .sliderRange(0.0, 20.0)
            .build()
      );
   private final Setting<Boolean> antiSuicide = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("anti-suicide")
            .description("Never set off an anchor whose blast, with a margin for error, would leave you dead or popping a totem.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("render")
            .description("Outline the anchor (or where it would go), green when its blast is safe for you, red when not.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> safeColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("safe-color")
            .description("Colour when the blast is safe for you.")
            .defaultValue(new SettingColor(255, 170, 60, 45))
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> unsafeColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("unsafe-color")
            .description("Colour when the blast would hurt you too much - it will not be set off.")
            .defaultValue(new SettingColor(255, 60, 60, 45))
            .visible(this.render::get)
            .build()
      );
   private final Setting<Boolean> debug = this.sgDebug
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Say in chat (only you see it) each click it makes and why it holds back.")
            .defaultValue(false)
            .build()
      );
   private static CrystalPvp.Preview preview;
   private final SlotReturn slots = new SlotReturn();
   private boolean engaged;
   private int sinceAction = 1000;
   private int extra;
   private BlockPos pending;
   private AnchorPvp.Step pendingStep;
   private int pendingAge = 1000;
   private String lastReason;
   @Nullable
   private AABB spot;
   private boolean spotSafe;

   public AnchorPvp() {
      super(
         SpecterClientAddon.PVP,
         "anchor-pvp",
         "Hold right-click and aim: places a respawn anchor, charges it with glowstone and sets it off, once the blast is safe for you. Does nothing in the nether."
      );
   }

   @Override
   public void onActivate() {
      this.resetState();
   }

   @Override
   public void onDeactivate() {
      this.endHold();
      if (this.mc.player != null) {
         this.slots.restore(this.switchBack.get());
      }

      this.resetState();
   }

   @Nullable
   public static CrystalPvp.Preview preview() {
      AnchorPvp module = Modules.get().get(AnchorPvp.class);
      return module != null && module.isActive() ? preview : null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.resetState();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.sinceAction = Math.min(this.sinceAction + 1, 1000);
      this.pendingAge = Math.min(this.pendingAge + 1, 1000);
      LocalPlayer player = this.mc.player;
      if (player != null && this.mc.level != null && this.mc.gameMode != null && player.isAlive()) {
         AnchorPvp.Plan plan = this.plan(player);
         if (!this.hold.held() || !Hands.canAct()) {
            this.endHold();
            if (this.mc.screen == null && !PvpLock.actedThisTick() && this.slots.active()) {
               this.slots.restore(this.switchBack.get());
            }
         } else if (PvpLock.heldByOther(this)) {
            if (this.engaged) {
               this.hold.suppress();
            }
         } else if (this.engaged
            || this.slots.holding()
            || plan != null && (!this.hold.onUseButton() || Hands.startsFrom(player.getMainHandItem(), Items.RESPAWN_ANCHOR, Items.GLOWSTONE))) {
            this.engaged = true;
            PvpLock.acquire(this);
            this.hold.suppress();
            if (plan != null && !PvpLock.actedThisTick()) {
               int delay = switch (plan.step()) {
                  case PLACE -> this.placeDelay.get();
                  case CHARGE -> this.chargeDelay.get();
                  case EXPLODE -> this.explodeDelay.get();
               };
               if (this.sinceAction > delay + this.extra) {
                  if (plan.route() != -2) {
                     this.slots.take(plan.route());
                  } else if (Hands.useOn(InteractionHand.MAIN_HAND, plan.hit())) {
                     this.pending = plan.pos();
                     this.pendingStep = plan.step();
                     this.pendingAge = 0;
                     this.sinceAction = 0;
                     this.extra = Pace.ticks(0, this.spread.get());
                     this.lastReason = null;
                     if (this.debug.get()) {
                        this.info("%s (+%d random ticks before the next).", new Object[]{switch (plan.step()) {
                           case PLACE -> "Placed an anchor";
                           case CHARGE -> "Charged the anchor";
                           case EXPLODE -> "Set the anchor off";
                        }, this.extra});
                     }
                  }
               }
            }
         }
      } else {
         this.endHold();
         this.slots.forget();
         preview = null;
         this.spot = null;
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.render.get() && this.spot != null) {
         SettingColor color = this.spotSafe ? this.safeColor.get() : this.unsafeColor.get();
         event.renderer.box(this.spot, color, color, ShapeMode.Both, 0);
      }
   }

   @Nullable
   private AnchorPvp.Plan plan(LocalPlayer player) {
      preview = null;
      this.spot = null;
      if (!Blast.anchorsExplode(this.mc.level)) {
         return null;
      } else if (Hands.crosshair() instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         BlockPos pos = hit.getBlockPos();
         if (!this.mc.level.getWorldBorder().isWithinBounds(pos)) {
            return null;
         } else {
            BlockState state = this.mc.level.getBlockState(pos);
            int window = Hands.confirmTicks();
            if (state.is(Blocks.RESPAWN_ANCHOR)) {
               boolean safe = this.previewAt(player, pos);
               this.spot = new AABB(pos);
               this.spotSafe = safe;
               int charges = state.getValue(RespawnAnchorBlock.CHARGE);
               if (charges == 0) {
                  if (pos.equals(this.pending) && this.pendingStep == AnchorPvp.Step.CHARGE && this.pendingAge < window) {
                     return this.skip("waiting for the server to charge it");
                  } else if (!safe) {
                     return this.skip("its blast would hurt you too much - not charging it");
                  } else {
                     int route = glowstoneRoute(player);
                     return route == -1 ? this.skip("no glowstone in the hotbar") : new AnchorPvp.Plan(AnchorPvp.Step.CHARGE, hit, pos, route);
                  }
               } else if (pos.equals(this.pending) && this.pendingStep == AnchorPvp.Step.EXPLODE && this.pendingAge < window) {
                  return this.skip("waiting for the server to set it off");
               } else if (!safe) {
                  return this.skip("its blast would hurt you too much");
               } else if (player.getOffhandItem().is(Items.GLOWSTONE) && charges < 4) {
                  return this.skip("glowstone in your offhand would charge it instead");
               } else if (player.isShiftKeyDown()) {
                  return this.skip("you are sneaking, so the click would not reach the anchor");
               } else {
                  int route = this.detonatorRoute(player);
                  return route == -1
                     ? this.skip("no non-glowstone item in the hotbar to set it off with")
                     : new AnchorPvp.Plan(AnchorPvp.Step.EXPLODE, hit, pos, route);
               }
            } else if (!this.place.get()) {
               return null;
            } else {
               BlockPos target = Placing.target(player, hit, Blocks.RESPAWN_ANCHOR.defaultBlockState());
               if (target == null) {
                  return null;
               } else {
                  this.spot = new AABB(target);
                  this.spotSafe = this.previewAt(player, target);
                  if (target.equals(this.pending) && this.pendingStep == AnchorPvp.Step.PLACE && this.pendingAge < window) {
                     return null;
                  } else if (!this.spotSafe) {
                     return this.skip("an anchor there would hurt you too much - not placing it");
                  } else if (!player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
                     int slot = Hands.hotbarSlot(Items.RESPAWN_ANCHOR);
                     return slot == -1 ? null : new AnchorPvp.Plan(AnchorPvp.Step.PLACE, hit, target, slot);
                  } else {
                     return new AnchorPvp.Plan(AnchorPvp.Step.PLACE, hit, target, -2);
                  }
               }
            }
         }
      } else {
         return null;
      }
   }

   private boolean previewAt(LocalPlayer player, BlockPos anchor) {
      float self = Blast.anchor(player, anchor);
      Player target = Targets.current(anchor.getCenter(), 10.0);
      float toTarget = target == null ? 0.0F : Blast.anchor(target, anchor);
      boolean safe = BlastMath.safe(self, this.maxSelfDamage.get().floatValue(), this.antiSuicide.get(), Targets.health(player));
      preview = new CrystalPvp.Preview(toTarget, self, safe, target == null ? null : target.getName().getString());
      return safe;
   }

   private static int glowstoneRoute(LocalPlayer player) {
      return player.getMainHandItem().is(Items.GLOWSTONE) ? -2 : Hands.hotbarSlot(Items.GLOWSTONE);
   }

   private int detonatorRoute(LocalPlayer player) {
      if (detonates(player.getMainHandItem())) {
         return -2;
      } else {
         int saved = this.slots.saved();
         Inventory inventory = player.getInventory();
         return Inventory.isHotbarSlot(saved) && detonates(inventory.getItem(saved)) ? saved : Hands.hotbarSlot(AnchorPvp::detonates);
      }
   }

   private static boolean detonates(ItemStack stack) {
      return !stack.is(Items.GLOWSTONE);
   }

   @Nullable
   private AnchorPvp.Plan skip(String reason) {
      if (this.debug.get() && !reason.equals(this.lastReason)) {
         this.info("Holding back: %s.", new Object[]{reason});
      }

      this.lastReason = reason;
      return null;
   }

   private void endHold() {
      PvpLock.release(this);
      if (this.engaged) {
         this.engaged = false;
         this.hold.release();
      }
   }

   private void resetState() {
      this.engaged = false;
      this.slots.forget();
      this.sinceAction = 1000;
      this.extra = 0;
      this.pending = null;
      this.pendingStep = null;
      this.pendingAge = 1000;
      this.lastReason = null;
      preview = null;
      this.spot = null;
      PvpLock.release(this);
   }

   private record Plan(AnchorPvp.Step step, BlockHitResult hit, BlockPos pos, int route) {
   }

   private static enum Step {
      PLACE,
      CHARGE,
      EXPLODE;
   }
}
