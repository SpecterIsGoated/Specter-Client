package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.perf.SectionThrottle;
import java.util.Collection;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Cuts the real cost of redstone on the client: chunk-section re-renders.
 *
 * <p>Every block-state change makes vanilla rebuild the mesh of the section around it (up to 8 sections
 * for a block on a corner), on the main thread when it is near you. A redstone clock flips dust power,
 * lamps, torches and repeaters 10-20 times a second, so a machine room keeps re-meshing the same sections
 * all the time - that is where the FPS goes, not in drawing the finished mesh.
 *
 * <ul>
 *   <li>Changes that look identical (dispenser/dropper "triggered", hopper "enabled", note block or door
 *       "powered"...) are skipped completely - the mesh would come out byte for byte the same.</li>
 *   <li>Visible redstone-only changes (dust power, lit lamps/torches, powered repeaters, comparators,
 *       observers, rails, crafters) re-render at most once per update interval per section; the first
 *       change shows at once and the last one is always shown.</li>
 *   <li>Shape changes (pistons moving, doors opening, blocks placed or broken, dust reconnecting) and
 *       anything you do yourself (repeater delay, comparator mode) are never touched.</li>
 *   <li>Redstone particles far from you are not spawned; moving piston blocks are only drawn nearby.</li>
 * </ul>
 */
public class RedstoneOptimizer extends Module {
   private static RedstoneOptimizer instance;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgEffects = this.settings.createGroup("Particles & Pistons");

   private final Setting<Boolean> dust = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("dust")
            .description("Redstone dust that only changes power level re-renders at most once per update interval. Its colour still catches up.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> components = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("components")
            .description("Repeaters, comparators, torches, lamps, observers, powered/detector rails and crafters that only switch on/off re-render at most once per update interval.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> skipInvisible = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("skip-invisible")
            .description("Redstone changes that don't change how the block looks (dispensers, droppers, hoppers, note blocks, door/trapdoor power...) never re-render the chunk.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> interval = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("update-interval")
            .description("Milliseconds between redstone re-renders of one chunk section. The first change in a quiet section always shows at once. Higher = more FPS, choppier clocks.")
            .defaultValue(200)
            .range(50, 1000)
            .sliderRange(50, 500)
            .build()
      );
   private final Setting<Boolean> particles = this.sgEffects
      .add(
         new BoolSetting.Builder()
            .name("particles")
            .description("Don't spawn the red dust particles from powered redstone dust, torches and repeaters, except close to you.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> particleRange = this.sgEffects
      .add(
         new IntSetting.Builder()
            .name("keep-particles-within")
            .description("Redstone particles still spawn this many blocks around you. 0 = none at all.")
            .defaultValue(6)
            .range(0, 32)
            .sliderRange(0, 16)
            .visible(this.particles::get)
            .build()
      );
   private final Setting<Boolean> pistons = this.sgEffects
      .add(
         new BoolSetting.Builder()
            .name("pistons")
            .description("Only draw blocks being pushed by pistons near you. Far away they just appear at their new spot (vanilla draws them up to 68 blocks).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> pistonRange = this.sgEffects
      .add(
         new IntSetting.Builder()
            .name("piston-distance")
            .description("Blocks being pushed are drawn moving within this distance.")
            .defaultValue(32)
            .range(8, 68)
            .sliderRange(8, 68)
            .visible(this.pistons::get)
            .build()
      );

   private final SectionThrottle throttle = new SectionThrottle();
   private ClientLevel level;
   /** Counts rendered frames: a section marked dirty is rebuilt once per frame, so more changes in the same frame are free. */
   private long frame;
   // setBlocksDirty and sendBlockUpdated both fire for one setBlock; decide once and reuse it.
   private long lastPos;
   private BlockState lastState;
   private boolean lastSkip;
   private int savedThisSecond;
   private int savedLastSecond;
   private long secondStart;

   public RedstoneOptimizer() {
      super(
         SpecterClientAddon.TOOLS,
         "redstone-optimizer",
         "More FPS around redstone machines: stops clocks from re-rendering the same chunks over and over, hides far redstone particles and far moving pistons."
      );
      instance = this;
   }

   /** The module when it is on, else null. Called from block-change mixins, so it must stay cheap. */
   public static RedstoneOptimizer on() {
      RedstoneOptimizer m = instance;
      return m != null && m.isActive() ? m : null;
   }

   @Override
   public void onActivate() {
      this.throttle.clear();
      this.lastState = null;
      this.level = this.mc.level;
      this.savedThisSecond = 0;
      this.savedLastSecond = 0;
      this.secondStart = System.currentTimeMillis();
   }

   @Override
   public void onDeactivate() {
      // Show the newest state of everything that was held back.
      if (this.mc.level != null && this.mc.level == this.level) {
         this.throttle.flushAll(this::renderSection);
      } else {
         this.throttle.clear();
      }

      this.lastState = null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.throttle.clear();
      this.lastState = null;
      this.level = null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.level != this.level) {
         // New world or dimension: the sections we were holding are gone.
         this.throttle.clear();
         this.lastState = null;
         this.level = this.mc.level;
      }

      if (this.level != null) {
         long now = System.currentTimeMillis();
         this.throttle.flush(now, this.frame, this::renderSection);
         this.throttle.prune(now, Math.max(2000L, this.interval.get() * 4L));
         if (now - this.secondStart >= 1000L) {
            this.savedLastSecond = (int)(this.savedThisSecond * 1000L / Math.max(1L, now - this.secondStart));
            this.savedThisSecond = 0;
            this.secondStart = now;
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      // Fires after this frame collected its dirty sections; changes from now on land in the next frame.
      this.frame++;
   }

   private void renderSection(long section) {
      if (this.mc.levelExtractor != null) {
         this.mc.levelExtractor.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
      }
   }

   /** From ClientLevel.setBlocksDirty (fires first). True = vanilla must not mark anything dirty. */
   public boolean skipSetBlocksDirty(BlockPos pos, BlockState oldState, BlockState newState) {
      boolean skip = this.decide(pos, oldState, newState);
      this.lastPos = pos.asLong();
      this.lastState = newState;
      this.lastSkip = skip;
      return skip;
   }

   /** From ClientLevel.sendBlockUpdated (fires second, for the same change). */
   public boolean skipSendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
      if (this.lastState == newState && this.lastPos == pos.asLong()) {
         this.lastState = null;
         return this.lastSkip;
      } else {
         // Only sendBlockUpdated fired (old == new on the setBlock path, or a direct call): decide alone.
         return (flags & 8) == 0 && this.decide(pos, oldState, newState);
      }
   }

   private boolean decide(BlockPos pos, BlockState oldState, BlockState newState) {
      if (oldState == newState || this.level == null) {
         return false;
      }

      Block block = newState.getBlock();
      if (oldState.getBlock() != block) {
         return false;
      }

      int kind = kind(block);
      if (kind == KIND_NONE || !onlyRedstoneProperties(oldState, newState)) {
         return false;
      }

      if (this.skipInvisible.get() && !this.mc.getModelManager().requiresRender(oldState, newState)) {
         // Same model group and same fluid = the mesh would be identical (the model manager groups states by
         // everything that changes geometry AND tint, e.g. dust power is a tint property so it never lands here).
         this.savedThisSecond++;
         return true;
      }

      boolean throttled = switch (kind) {
         case KIND_DUST -> this.dust.get();
         case KIND_COMPONENT -> this.components.get();
         default -> false;
      };
      if (!throttled) {
         return false;
      }

      if (this.throttle.allow(SectionPos.asLong(pos), System.currentTimeMillis(), this.frame, this.interval.get())) {
         return false;
      }

      this.savedThisSecond++;
      return true;
   }

   private static final int KIND_NONE = 0;
   private static final int KIND_DUST = 1;
   /** Visible on/off state - throttled. */
   private static final int KIND_COMPONENT = 2;
   /** Only ever skipped when the change is invisible (doors etc. are never throttled). */
   private static final int KIND_INVISIBLE_ONLY = 3;

   private static int kind(Block block) {
      if (block instanceof RedstoneWireBlock) {
         return KIND_DUST;
      } else if (block instanceof DiodeBlock
         || block instanceof RedstoneTorchBlock
         || block instanceof RedstoneLampBlock
         || block instanceof ObserverBlock
         || block instanceof PoweredRailBlock
         || block instanceof DetectorRailBlock
         || block instanceof CrafterBlock) {
         return KIND_COMPONENT;
      } else {
         return !(block instanceof DispenserBlock)
               && !(block instanceof HopperBlock)
               && !(block instanceof NoteBlock)
               && !(block instanceof TargetBlock)
               && !(block instanceof DaylightDetectorBlock)
               && !(block instanceof BellBlock)
               && !(block instanceof DoorBlock)
               && !(block instanceof TrapDoorBlock)
               && !(block instanceof FenceGateBlock)
            ? KIND_NONE
            : KIND_INVISIBLE_ONLY;
      }
   }

   /**
    * True when every property that changed is a redstone signal property. Facing, connections, repeater
    * delay, comparator mode, door "open", piston "extended" etc. all make it false, so those re-render as
    * normal.
    */
   private static boolean onlyRedstoneProperties(BlockState a, BlockState b) {
      Collection<Property<?>> properties = a.getProperties();
      boolean any = false;

      for (Property<?> p : properties) {
         if (!a.getValue(p).equals(b.getValue(p))) {
            if (!isSignalProperty(p)) {
               return false;
            }

            any = true;
         }
      }

      return any;
   }

   private static boolean isSignalProperty(Property<?> p) {
      return p == BlockStateProperties.POWER
         || p == BlockStateProperties.POWERED
         || p == BlockStateProperties.LIT
         || p == BlockStateProperties.LOCKED
         || p == BlockStateProperties.TRIGGERED
         || p == BlockStateProperties.CRAFTING
         || p == BlockStateProperties.ENABLED;
   }

   /** From ClientLevel.doAnimateTick. False = don't let this block spawn its particles. */
   public boolean allowAnimateTick(Block block, BlockPos pos) {
      if (!this.particles.get()
         || !(block instanceof RedstoneWireBlock) && !(block instanceof RedstoneTorchBlock) && !(block instanceof RepeaterBlock)) {
         return true;
      }

      int range = this.particleRange.get();
      if (range <= 0 || this.mc.player == null) {
         return false;
      }

      return this.mc.player.blockPosition().distSqr(pos) <= (double)range * range;
   }

   /** From PistonHeadRenderer.getViewDistance. */
   public int pistonViewDistance(int vanilla) {
      return this.pistons.get() ? Math.min(vanilla, this.pistonRange.get()) : vanilla;
   }

   @Override
   public String getInfoString() {
      return this.savedLastSecond > 0 ? this.savedLastSecond + "/s held" : null;
   }
}
