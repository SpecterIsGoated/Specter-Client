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
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class StasisFinder extends Module {
   private static final int STILL_TICKS = 40;
   private static final double STILL_MOVE_SQ = 4.0E-4;
   private static final double TRACKED = 48.0;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> stillWater = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("still-in-water")
            .description("Also count a pearl that has sat still in water for 2 seconds, not just one in a soul sand bubble column.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> chat = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("chat")
            .description("Say in chat where each stasis pearl is, who threw it, and when one gets used. Only you see it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> sound = this.sgGeneral
      .add(new BoolSetting.Builder().name("sound").description("Play a pling when one is found. Only you hear it.").defaultValue(true).build());
   private final Setting<Integer> forgetDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("forget-distance")
            .description("Forget pearls this far away (blocks).")
            .defaultValue(512)
            .min(64)
            .sliderRange(64, 4096)
            .build()
      );
   private final Setting<SettingColor> color = this.sgRender
      .add(new ColorSetting.Builder().name("color").description("Beam and box color.").defaultValue(new SettingColor(190, 80, 255, 80)).build());
   private final Setting<Double> beamHeight = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("beam-height")
            .description("How far the beam rises above the pearl.")
            .defaultValue(200.0)
            .min(0.0)
            .sliderRange(0.0, 384.0)
            .build()
      );
   private final Setting<Double> beamWidth = this.sgRender
      .add(new DoubleSetting.Builder().name("beam-width").description("How thick the beam is.").defaultValue(0.4).min(0.05).sliderRange(0.1, 2.0).build());
   private final Map<Integer, StasisFinder.Found> found = new LinkedHashMap<>();
   private final Map<Integer, StasisFinder.Still> watching = new HashMap<>();
   private final Color lineColor = new Color();
   private ClientLevel lastWorld;

   public StasisFinder() {
      super(
         SpecterClientAddon.ESP,
         "stasis-finder",
         "Finds ender pearls held in stasis chambers (someone's teleport home) and marks them with a beam. Sends nothing."
      );
   }

   @Override
   public void onActivate() {
      this.found.clear();
      this.watching.clear();
      this.lastWorld = this.mc.level;
   }

   @Override
   public String getInfoString() {
      return this.found.isEmpty() ? null : Integer.toString(this.found.size());
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.found.clear();
      this.watching.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         if (this.mc.level != this.lastWorld) {
            this.found.clear();
            this.watching.clear();
            this.lastWorld = this.mc.level;
         }

         Map<Integer, Boolean> present = new HashMap<>();

         for (Entity e : this.mc.level.entitiesForRendering()) {
            if (e instanceof ThrownEnderpearl pearl && !pearl.isRemoved()) {
               present.put(pearl.getId(), Boolean.TRUE);
               StasisFinder.Found f = this.found.get(pearl.getId());
               if (f != null) {
                  f.pos = pearl.position();
                  if (f.owner == null) {
                     f.owner = ownerName(pearl);
                  }
               } else if (this.stasis(pearl)) {
                  this.add(pearl);
               }
            }
         }

         this.watching.keySet().removeIf(id -> !present.containsKey(id));
         Vec3 me = this.mc.player.position();
         double forgetSq = (double)this.forgetDistance.get().intValue() * this.forgetDistance.get().intValue();
         Iterator<Entry<Integer, StasisFinder.Found>> it = this.found.entrySet().iterator();

         while (it.hasNext()) {
            StasisFinder.Found f = it.next().getValue();
            if (!present.containsKey(f.id)) {
               double distSq = f.pos.distanceToSqr(me);
               if (distSq < 2304.0 && this.mc.level.getChunkSource().hasChunk((int)Math.floor(f.pos.x) >> 4, (int)Math.floor(f.pos.z) >> 4)) {
                  if (this.chat.get()) {
                     this.info("%s", new Object[]{"Stasis pearl at " + where(f.pos) + (f.owner != null ? " (" + f.owner + ")" : "") + " was used."});
                  }

                  it.remove();
               } else if (distSq > forgetSq) {
                  it.remove();
               }
            }
         }
      }
   }

   private boolean stasis(ThrownEnderpearl pearl) {
      BlockPos pos = pearl.blockPosition();
      if (upwardColumn(this.mc.level.getBlockState(pos)) || upwardColumn(this.mc.level.getBlockState(pos.below()))) {
         return true;
      } else if (!this.stillWater.get()) {
         return false;
      } else {
         boolean wet = pearl.isInWater() || this.mc.level.getFluidState(pos).is(FluidTags.WATER);
         StasisFinder.Still s = this.watching.computeIfAbsent(pearl.getId(), id -> new StasisFinder.Still(pearl.position()));
         Vec3 now = pearl.position();
         if (wet && now.distanceToSqr(s.last) < 4.0E-4) {
            s.ticks++;
         } else {
            s.ticks = 0;
         }

         s.last = now;
         return s.ticks >= 40;
      }
   }

   private static boolean upwardColumn(BlockState state) {
      return state.is(Blocks.BUBBLE_COLUMN) && !state.getValue(BubbleColumnBlock.DRAG_DOWN);
   }

   private void add(ThrownEnderpearl pearl) {
      StasisFinder.Found f = new StasisFinder.Found(pearl.getId(), pearl.position(), ownerName(pearl));
      this.found.put(f.id, f);
      this.watching.remove(f.id);
      if (this.sound.get()) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 1.0F));
      }

      if (this.chat.get()) {
         this.info(
            "%s",
            new Object[]{
               "Stasis pearl at "
                  + where(f.pos)
                  + ", "
                  + Math.round(Math.sqrt(f.pos.distanceToSqr(this.mc.player.position())))
                  + " blocks away"
                  + (f.owner != null ? " - thrown by " + f.owner : "")
                  + "."
            }
         );
      }
   }

   private static String ownerName(ThrownEnderpearl pearl) {
      return pearl.getOwner() instanceof Player player ? player.getGameProfile().name() : null;
   }

   private static String where(Vec3 p) {
      return (int)Math.floor(p.x) + " " + (int)Math.floor(p.y) + " " + (int)Math.floor(p.z);
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (!this.found.isEmpty()) {
         SettingColor c = this.color.get();
         this.lineColor.set(c.r, c.g, c.b, 255);
         double half = this.beamWidth.get() / 2.0;
         double height = this.beamHeight.get();

         for (StasisFinder.Found f : this.found.values()) {
            double x = f.pos.x;
            double y = f.pos.y;
            double z = f.pos.z;
            event.renderer.box(x - 0.2, y - 0.2, z - 0.2, x + 0.2, y + 0.2, z + 0.2, c, this.lineColor, ShapeMode.Both, 0);
            if (height > 0.0) {
               event.renderer.box(x - half, y + 0.2, z - half, x + half, y + height, z + half, c, this.lineColor, ShapeMode.Sides, 0);
            }
         }
      }
   }

   private static final class Found {
      final int id;
      Vec3 pos;
      String owner;

      Found(int id, Vec3 pos, String owner) {
         this.id = id;
         this.pos = pos;
         this.owner = owner;
      }
   }

   private static final class Still {
      Vec3 last;
      int ticks;

      Still(Vec3 last) {
         this.last = last;
      }
   }
}
