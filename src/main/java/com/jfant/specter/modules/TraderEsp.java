package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.phys.Vec3;

public class TraderEsp extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> traders = this.sgGeneral
      .add(new BoolSetting.Builder().name("wandering-traders").description("Box wandering traders.").defaultValue(true).build());
   private final Setting<Boolean> llamas = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("llamas")
            .description("Box llamas, including the trader llamas that follow a wandering trader.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(new ColorSetting.Builder().name("side-color").description("Fill colour of the boxes.").defaultValue(new SettingColor(255, 140, 20, 50)).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(new ColorSetting.Builder().name("line-color").description("Outline and tracer colour.").defaultValue(new SettingColor(255, 160, 40, 255)).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the boxes are drawn."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw a line from you to each one.").defaultValue(false).build());
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Only draw within this many blocks of you.")
            .defaultValue(256)
            .min(8)
            .sliderRange(16, 1024)
            .build()
      );

   public TraderEsp() {
      super(SpecterClientAddon.ESP, "trader-esp", "Orange boxes around wandering traders and llamas.");
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.level != null && this.mc.player != null) {
         boolean showTraders = this.traders.get();
         boolean showLlamas = this.llamas.get();
         if (showTraders || showLlamas) {
            SettingColor sides = this.sideColor.get();
            SettingColor lines = this.lineColor.get();
            ShapeMode mode = this.shapeMode.get();
            Vec3 from = RenderUtils.center;
            boolean drawTracers = this.tracers.get() && from != null;
            double maxSq = (double)this.renderDistance.get().intValue() * this.renderDistance.get().intValue();

            for (Entity entity : this.mc.level.entitiesForRendering()) {
               boolean match = showTraders && entity instanceof WanderingTrader || showLlamas && entity instanceof Llama;
               if (match && !(entity.distanceToSqr(this.mc.player) > maxSq)) {
                  Vec3 pos = entity.getPosition(event.tickDelta);
                  double half = entity.getBbWidth() / 2.0F;
                  double height = entity.getBbHeight();
                  event.renderer.box(pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + height, pos.z + half, sides, lines, mode, 0);
                  if (drawTracers) {
                     event.renderer.line(from.x, from.y, from.z, pos.x, pos.y + height / 2.0, pos.z, lines);
                  }
               }
            }
         }
      }
   }
}
