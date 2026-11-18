package com.jfant.specter.move;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.core.MixinPlugin;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.RenderBlockEntityEvent;
import com.jfant.specter.core.events.world.AmbientOcclusionEvent;
import com.jfant.specter.core.events.world.ChunkOcclusionEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BlockListSetting;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import java.util.List;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

public class Xray extends Module {
   public static final List<Block> DEFAULT_BLOCKS = List.of(
      Blocks.COAL_ORE,
      Blocks.DEEPSLATE_COAL_ORE,
      Blocks.IRON_ORE,
      Blocks.DEEPSLATE_IRON_ORE,
      Blocks.COPPER_ORE,
      Blocks.DEEPSLATE_COPPER_ORE,
      Blocks.GOLD_ORE,
      Blocks.DEEPSLATE_GOLD_ORE,
      Blocks.NETHER_GOLD_ORE,
      Blocks.LAPIS_ORE,
      Blocks.DEEPSLATE_LAPIS_ORE,
      Blocks.REDSTONE_ORE,
      Blocks.DEEPSLATE_REDSTONE_ORE,
      Blocks.DIAMOND_ORE,
      Blocks.DEEPSLATE_DIAMOND_ORE,
      Blocks.EMERALD_ORE,
      Blocks.DEEPSLATE_EMERALD_ORE,
      Blocks.NETHER_QUARTZ_ORE,
      Blocks.ANCIENT_DEBRIS,
      Blocks.SPAWNER
   );
   private static final int RELOAD_GAP = 10;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(
         new BlockListSetting.Builder()
            .name("blocks")
            .description(
               "The blocks that stay visible. Everything else is hidden or turned into a ghost. Add chests, spawners, bases' storage and so on as you like."
            )
            .defaultValue(DEFAULT_BLOCKS)
            .onChanged(v -> this.changed())
            .build()
      );
   private final Setting<Integer> opacity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("opacity")
            .description(
               "How visible the other blocks stay, in percent. 0 removes them completely; low values leave faint ghost blocks so you keep your bearings. Does not fade smoothly: it is baked into the rebuilt chunks. With a shader pack it counts as 0."
            )
            .defaultValue(8)
            .range(0, 90)
            .sliderRange(0, 90)
            .onChanged(v -> this.changed())
            .build()
      );
   private final Setting<Boolean> exposedOnly = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("exposed-only")
            .description(
               "Show a listed block only where it touches air or a see-through block. Ore buried in solid rock stays hidden. That defeats the fake ores some servers' anti-xray plants inside rock, but it hides every real vein you have not uncovered as well."
            )
            .defaultValue(false)
            .onChanged(v -> this.changed())
            .build()
      );
   private final Setting<Boolean> hideBlockEntities = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hide-block-entities")
            .description(
               "Also hide chests, signs, banners, beds and other block entities that are not on the list. Off leaves them drawn on top of the empty space."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> brighten = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("full-bright")
            .description("Light the world completely while X-Ray is on (fades in and out), so ore in dark caves and tunnels is not black.")
            .defaultValue(true)
            .build()
      );
   private static volatile Xray.Snapshot snapshot;
   private final Brightness.Layer layer = Brightness.layer();
   private boolean reloadPending;
   private int sinceReload = 10;
   private boolean shadersWere;
   private static final Direction[] DIRECTIONS = Direction.values();

   public Xray() {
      super(
         SpecterClientAddon.ESP,
         "xray",
         "Shows only the blocks you pick (ore by default) and fades the rest out so ore shows through rock. Client-side only: it uses chunks the server already sent, and fake ore from anti-xray looks the same as real ore. Rebuilds chunks when switched."
      );
   }

   @Override
   public void onActivate() {
      this.shadersWere = shaders();
      this.rebuild();
      if (this.brighten.get()) {
         this.layer.set(1.0, 0.0, Spec.WORLD_FADE);
      }

      this.reloadNow();
   }

   @Override
   public void onDeactivate() {
      snapshot = null;
      this.layer.set(0.0, 0.0, Spec.WORLD_FADE);
      this.reloadPending = false;
      this.reloadNow();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.layer.snapOff();
      this.reloadPending = false;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      boolean shaders = shaders();
      if (shaders != this.shadersWere) {
         this.shadersWere = shaders;
         this.changed();
      }

      this.layer.set(this.brighten.get() ? 1.0 : 0.0, 0.0, Spec.WORLD_FADE);
      if (this.sinceReload < 10) {
         this.sinceReload++;
      }

      if (this.reloadPending && this.sinceReload >= 10) {
         this.reloadNow();
      }
   }

   private void changed() {
      if (this.isActive()) {
         this.rebuild();
         this.reloadPending = true;
      }
   }

   private void rebuild() {
      int alpha = shaders() ? 0 : Math.round(this.opacity.get().intValue() * 2.55F);
      snapshot = new Xray.Snapshot(Set.copyOf(this.blocks.get()), Math.clamp((long)alpha, 0, 254), this.exposedOnly.get());
   }

   private void reloadNow() {
      this.reloadPending = false;
      this.sinceReload = 0;
      if (this.mc.levelRenderer != null && this.mc.level != null) {
         this.mc.levelExtractor.allChanged();
      }
   }

   private static boolean shaders() {
      try {
         return MixinPlugin.isIrisPresent && IrisApi.getInstance().isShaderPackInUse();
      } catch (Throwable var1) {
         return false;
      }
   }

   @EventHandler
   private void onChunkOcclusion(ChunkOcclusionEvent event) {
      event.cancel();
   }

   @EventHandler
   private void onAmbientOcclusion(AmbientOcclusionEvent event) {
      event.lightLevel = 1.0F;
   }

   @EventHandler
   private void onRenderBlockEntity(RenderBlockEntityEvent event) {
      if (this.hideBlockEntities.get()) {
         if (isBlocked(this.mc.level.getBlockState(event.blockEntityState.blockPos).getBlock(), null, event.blockEntityState.blockPos)) {
            event.cancel();
         }
      }
   }

   public static boolean active() {
      return snapshot != null;
   }

   public static int alpha(BlockState state, BlockGetter view, BlockPos pos) {
      Xray.Snapshot s = snapshot;
      if (s == null) {
         return -1;
      } else {
         return blocked(s, state.getBlock(), view, pos) ? s.alpha : -1;
      }
   }

   public static int layerAlpha(BlockState state) {
      Xray.Snapshot s = snapshot;
      if (s == null) {
         return -1;
      } else {
         return s.targets.contains(state.getBlock()) ? -1 : s.alpha;
      }
   }

   public static boolean isBlocked(Block block, BlockGetter view, BlockPos pos) {
      Xray.Snapshot s = snapshot;
      return s != null && blocked(s, block, view, pos);
   }

   private static boolean blocked(Xray.Snapshot s, Block block, BlockGetter view, BlockPos pos) {
      return !s.targets.contains(block) ? true : s.exposedOnly && view != null && pos != null && !exposed(view, pos);
   }

   private static boolean exposed(BlockGetter view, BlockPos pos) {
      MutableBlockPos at = new MutableBlockPos();

      for (Direction direction : DIRECTIONS) {
         if (!view.getBlockState(at.setWithOffset(pos, direction)).isSolidRender()) {
            return true;
         }
      }

      return false;
   }

   public static boolean drawSide(BlockState state, BlockGetter view, BlockPos pos, Direction facing, boolean original) {
      return drawSideOf(state, view, pos, facing, original);
   }

   public static boolean drawFaceAgainst(BlockState state, BlockGetter view, BlockPos neighbour, Direction side, boolean original) {
      Xray.Snapshot s = snapshot;
      return s != null && !original && s.targets.contains(state.getBlock())
         ? drawSideOf(state, view, neighbour.relative(side.getOpposite()), side, original)
         : original;
   }

   private static boolean drawSideOf(BlockState state, BlockGetter view, BlockPos pos, Direction facing, boolean original) {
      Xray.Snapshot s = snapshot;
      if (s != null && !original && !blocked(s, state.getBlock(), view, pos)) {
         BlockPos adjPos = pos.relative(facing);
         BlockState adj = view.getBlockState(adjPos);
         return adj.getFaceOcclusionShape(facing.getOpposite()) != Shapes.block()
            || adj.getBlock() != state.getBlock()
            || !adj.isSolidRender()
            || blocked(s, adj.getBlock(), view, adjPos);
      } else {
         return original;
      }
   }

   @Override
   public String getInfoString() {
      return this.opacity.get() + "%";
   }

   private record Snapshot(Set<Block> targets, int alpha, boolean exposedOnly) {
   }
}
