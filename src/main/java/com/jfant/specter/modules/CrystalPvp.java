package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public class CrystalPvp extends Module {
   private static final double TARGET_RANGE = 12.0;
   private static final int COUNTER_CAP = 1000;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final HoldKey hold = new HoldKey(this.sgGeneral, "it breaks and places crystals");
   private final Setting<Integer> breakDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("break-delay")
            .description("Ticks to wait after the last click before breaking a crystal.")
            .defaultValue(0)
            .range(0, 10)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Integer> placeDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("place-delay")
            .description("Ticks to wait after the last click before placing a crystal.")
            .defaultValue(0)
            .range(0, 10)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Integer> spread = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("random-spread")
            .description("Up to this many extra ticks on each delay, drawn fresh every click, so no two clicks are timed alike. 0 = exact.")
            .defaultValue(1)
            .range(0, 4)
            .sliderRange(0, 4)
            .build()
      );
   private final Setting<Boolean> autoObsidian = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-obsidian")
            .description("Looking at any other block, put obsidian against it first (needs obsidian and crystals in the hotbar).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> obsidianDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("obsidian-delay")
            .description("Ticks to wait after the last click before placing obsidian.")
            .defaultValue(2)
            .range(0, 10)
            .sliderRange(0, 10)
            .visible(this.autoObsidian::get)
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
            .description("Never set off a crystal that would do you more than this (2 = one heart).")
            .defaultValue(20.0)
            .range(0.0, 36.0)
            .sliderRange(0.0, 20.0)
            .build()
      );
   private final Setting<Boolean> antiSuicide = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("anti-suicide")
            .description("Never set off a crystal whose blast, with a margin for error, would leave you dead or popping a totem.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("render")
            .description("Outline where the crystal would go, and the crystal about to be broken.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> safeColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("safe-color")
            .description("Spot colour when the blast is safe for you.")
            .defaultValue(new SettingColor(180, 90, 255, 45))
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> unsafeColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("unsafe-color")
            .description("Spot colour when the blast would hurt you too much - it will not be set off.")
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
   private int mainAge = 1000;
   private int lastSelected = -1;
   private int pendingBreakId = -1;
   private int pendingBreakAge = 1000;
   private BlockPos pendingCrystal;
   private int pendingCrystalAge = 1000;
   private BlockPos pendingObsidian;
   private int pendingObsidianAge = 1000;
   private String lastReason;
   @Nullable
   private AABB spot;
   private boolean spotSafe;

   public CrystalPvp() {
      super(
         SpecterClientAddon.PVP,
         "crystal-pvp",
         "Hold right-click and aim: breaks the crystal you look at, puts a crystal on the obsidian you look at, and obsidian on anything else. Checks the blast is safe for you first."
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
      CrystalPvp module = Modules.get().get(CrystalPvp.class);
      return module != null && module.isActive() ? preview : null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.resetState();
      PvpLock.reset();
      Targets.reset();
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      Targets.hit(event.entity);
      if (event.entity instanceof EndCrystal crystal) {
         this.markBreak(crystal);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.age();
      LocalPlayer player = this.mc.player;
      if (player != null && this.mc.level != null && this.mc.gameMode != null && player.isAlive()) {
         int selected = player.getInventory().getSelectedSlot();
         if (selected != this.lastSelected) {
            this.lastSelected = selected;
            this.mainAge = 0;
         }

         CrystalPvp.Plan plan = this.plan(player);
         if (!this.hold.held() || !Hands.canAct()) {
            this.endHold();
            if (this.mc.gui.screen() == null && !PvpLock.actedThisTick() && this.slots.active()) {
               this.slots.restore(this.switchBack.get());
            }
         } else if (PvpLock.heldByOther(this)) {
            if (this.engaged) {
               this.hold.suppress();
            }
         } else if (this.engaged
            || this.slots.holding()
            || plan != null && (!this.hold.onUseButton() || Hands.startsFrom(player.getMainHandItem(), Items.END_CRYSTAL, Items.OBSIDIAN))) {
            this.engaged = true;
            PvpLock.acquire(this);
            this.hold.suppress();
            if (plan != null && !PvpLock.actedThisTick()) {
               switch (plan.step()) {
                  case BREAK:
                     this.breakCrystal(plan);
                     break;
                  case CRYSTAL:
                     if (this.use(plan, this.placeDelay.get())) {
                        this.pendingCrystal = plan.target();
                        this.pendingCrystalAge = 0;
                     }
                     break;
                  case OBSIDIAN:
                     if (this.use(plan, this.obsidianDelay.get())) {
                        this.pendingObsidian = plan.target();
                        this.pendingObsidianAge = 0;
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
   private CrystalPvp.Plan plan(LocalPlayer player) {
      preview = null;
      this.spot = null;
      HitResult hit = Hands.crosshair();
      int window = Hands.confirmTicks();
      Level world = this.mc.level;
      if (this.pendingCrystal != null && this.hasCrystal(this.pendingCrystal)) {
         this.pendingCrystal = null;
      }

      if (hit instanceof EntityHitResult entityHit) {
         if (entityHit.getEntity() instanceof EndCrystal crystal && crystal.isAlive()) {
            boolean safe = this.previewAt(player, crystal.position(), null);
            this.spot = crystal.getBoundingBox();
            this.spotSafe = safe;
            if (crystal.getId() == this.pendingBreakId && this.pendingBreakAge < window) {
               return this.skip("waiting for the server to break the last one");
            } else if (!safe) {
               return this.skip("breaking it would hurt you too much");
            } else {
               int weapon = Hands.damagingRoute(crystal);
               return weapon == -1
                  ? this.skip("nothing in the hotbar does damage (weakness?)")
                  : new CrystalPvp.Plan(CrystalPvp.Step.BREAK, crystal, null, null, weapon);
            }
         } else {
            return null;
         }
      } else if (hit instanceof BlockHitResult blockHit && blockHit.getType() == Type.BLOCK) {
         BlockPos pos = blockHit.getBlockPos();
         if (!world.getWorldBorder().isWithinBounds(pos)) {
            return null;
         } else {
            BlockState state = world.getBlockState(pos);
            if (isBase(state)) {
               if (!this.canHost(pos)) {
                  return null;
               } else {
                  boolean safe = this.previewAt(player, Blast.crystalAt(pos), null);
                  this.spot = Blast.crystalBox(pos);
                  this.spotSafe = safe;
                  if (pos.equals(this.pendingCrystal) && this.pendingCrystalAge < window) {
                     return this.skip("waiting for the server to place the last one");
                  } else if (!safe) {
                     return this.skip("a crystal there would hurt you too much");
                  } else {
                     int route = Hands.route(Items.END_CRYSTAL);
                     return route == -1 ? this.skip("no crystals in the hotbar") : new CrystalPvp.Plan(CrystalPvp.Step.CRYSTAL, null, blockHit, pos, route);
                  }
               }
            } else if (!this.autoObsidian.get()) {
               return null;
            } else if (Hands.route(Items.END_CRYSTAL) == -1) {
               return null;
            } else {
               int route = Hands.route(Items.OBSIDIAN);
               if (route == -1) {
                  return null;
               } else {
                  BlockPos target = this.obsidianTarget(player, blockHit);
                  if (target == null) {
                     return null;
                  } else {
                     boolean safe = this.previewAt(player, Blast.crystalAt(target), target);
                     this.spot = new AABB(target);
                     this.spotSafe = safe;
                     if (target.equals(this.pendingObsidian) && this.pendingObsidianAge < window) {
                        return null;
                     } else {
                        return !safe
                           ? this.skip("a crystal on obsidian there would hurt you too much - not placing it")
                           : new CrystalPvp.Plan(CrystalPvp.Step.OBSIDIAN, null, blockHit, target, route);
                     }
                  }
               }
            }
         }
      } else {
         return null;
      }
   }

   private boolean previewAt(LocalPlayer player, Vec3 at, @Nullable BlockPos obsidian) {
      float self = Blast.crystal(player, at, obsidian);
      Player target = Targets.current(at, 12.0);
      float toTarget = target == null ? 0.0F : Blast.crystal(target, at, obsidian);
      boolean safe = BlastMath.safe(self, this.maxSelfDamage.get().floatValue(), this.antiSuicide.get(), Targets.health(player));
      preview = new CrystalPvp.Preview(toTarget, self, safe, target == null ? null : target.getName().getString());
      return safe;
   }

   @Nullable
   private CrystalPvp.Plan skip(String reason) {
      if (this.debug.get() && !reason.equals(this.lastReason)) {
         this.info("Holding back: %s.", new Object[]{reason});
      }

      this.lastReason = reason;
      return null;
   }

   private void breakCrystal(CrystalPvp.Plan plan) {
      if (this.sinceAction > this.breakDelay.get() + this.extra) {
         if (plan.route() != -2) {
            this.slots.take(plan.route());
         } else if (this.mainAge >= 1) {
            if (Hands.attack(plan.crystal())) {
               this.markBreak(plan.crystal());
               this.acted("Broke a crystal");
            }
         }
      }
   }

   private boolean use(CrystalPvp.Plan plan, int delay) {
      if (this.sinceAction <= delay + this.extra) {
         return false;
      } else if (plan.route() != -2) {
         this.slots.take(plan.route());
         return false;
      } else {
         InteractionHand hand = Hands.handOf(plan.step() == CrystalPvp.Step.CRYSTAL ? Items.END_CRYSTAL : Items.OBSIDIAN);
         if (!Hands.useOn(hand, plan.hit())) {
            return false;
         } else {
            this.acted(plan.step() == CrystalPvp.Step.CRYSTAL ? "Placed a crystal" : "Placed obsidian");
            return true;
         }
      }
   }

   private void acted(String what) {
      this.sinceAction = 0;
      this.extra = Pace.ticks(0, this.spread.get());
      this.lastReason = null;
      if (this.debug.get()) {
         this.info("%s (+%d random ticks before the next).", new Object[]{what, this.extra});
      }
   }

   private void markBreak(EndCrystal crystal) {
      this.pendingBreakId = crystal.getId();
      this.pendingBreakAge = 0;
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
      this.mainAge = 1000;
      this.lastSelected = -1;
      this.pendingBreakId = -1;
      this.pendingBreakAge = 1000;
      this.pendingCrystal = null;
      this.pendingCrystalAge = 1000;
      this.pendingObsidian = null;
      this.pendingObsidianAge = 1000;
      this.lastReason = null;
      preview = null;
      this.spot = null;
      PvpLock.release(this);
   }

   private void age() {
      this.sinceAction = Math.min(this.sinceAction + 1, 1000);
      this.mainAge = Math.min(this.mainAge + 1, 1000);
      this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
      this.pendingCrystalAge = Math.min(this.pendingCrystalAge + 1, 1000);
      this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
   }

   private static boolean isBase(BlockState state) {
      return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
   }

   private boolean canHost(BlockPos base) {
      BlockPos above = base.above();
      if (!this.mc.level.isEmptyBlock(above)) {
         return false;
      } else {
         AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 2, above.getZ() + 1);

         for (Entity entity : this.mc.level.getEntities(null, space)) {
            if (entity.isAlive() && (!(entity instanceof EndCrystal crystal) || crystal.getId() != this.pendingBreakId)) {
               return false;
            }
         }

         return true;
      }
   }

   private boolean hasCrystal(BlockPos base) {
      BlockPos above = base.above();

      for (EndCrystal crystal : this.mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(above), Entity::isAlive)) {
         if (crystal.blockPosition().equals(above)) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   private BlockPos obsidianTarget(LocalPlayer player, BlockHitResult hit) {
      BlockPos target = Placing.target(player, hit, Blocks.OBSIDIAN.defaultBlockState());
      return target != null && this.canHost(target) ? target : null;
   }

   private record Plan(CrystalPvp.Step step, @Nullable EndCrystal crystal, @Nullable BlockHitResult hit, @Nullable BlockPos target, int route) {
   }

   public record Preview(float target, float self, boolean safe, @Nullable String targetName) {
   }

   private static enum Step {
      BREAK,
      CRYSTAL,
      OBSIDIAN;
   }
}
