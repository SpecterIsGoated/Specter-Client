package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.RenderItemEntityEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * Item Physics: dropped items lie on the ground like real objects instead of hovering and spinning. Flat items (tools,
 * ingots, totems) lie face down at a random angle; block items sit on the ground at a random turn; stacks pile up.
 * While falling they tumble, and settle when they land. Only how items are DRAWN changes - their real position,
 * collision and pickup are the game's own, untouched.
 */
public class ItemPhysics extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> tumble = this.sgGeneral
      .add(new BoolSetting.Builder().name("tumble").description("Items spin while they fly through the air.").defaultValue(true).build());
   private final Setting<Double> tumbleSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("tumble-speed").description("How fast they spin in the air.").defaultValue(1.0).range(0.1, 4.0).sliderRange(0.1, 3.0).visible(this.tumble::get).build());

   private final RandomSource random = RandomSource.create();

   public ItemPhysics() {
      super(SpecterClientAddon.VISUALS, "item-physics", "Dropped items lie flat on the ground like real objects instead of floating and spinning. Visual only, pickup is unchanged.");
   }

   @EventHandler
   private void onRenderItem(RenderItemEntityEvent event) {
      ItemEntityRenderState state = event.renderState;
      if (state.item.isEmpty()) return;
      ItemEntity entity = event.itemEntity;
      PoseStack pose = event.matrixStack;
      AABB box = state.item.getModelBoundingBox();
      boolean flat = box.getZsize() <= 0.0625;
      int id = entity != null ? entity.getId() : state.seed;
      // a stable random angle per item, so each one lies differently and keeps lying the same way
      float restYaw = (float)(((id * 0x9E3779B9L) >>> 8 & 0xFFFF) / 65536.0 * 360.0);
      boolean airborne = entity != null && !entity.onGround() && !entity.isInWater();
      float age = state.ageInTicks;

      pose.pushPose();
      if (airborne && this.tumble.get()) {
         float spin = age * 18.0F * this.tumbleSpeed.get().floatValue();
         pose.translate(0.0F, 0.15F, 0.0F);
         pose.rotate(Axis.YP.rotationDegrees(restYaw));
         pose.rotate(Axis.XP.rotationDegrees(spin));
         pose.rotate(Axis.ZP.rotationDegrees(spin * 0.6F));
      } else if (flat) {
         // face down on the ground, just above it so it never flickers into the block below
         pose.translate(0.0F, 0.02F, 0.0F);
         pose.rotate(Axis.YP.rotationDegrees(restYaw));
         pose.rotate(Axis.XP.rotationDegrees(90.0F));
      } else {
         // block models: resting on their bottom face
         pose.translate(0.0F, (float)-box.minY, 0.0F);
         pose.rotate(Axis.YP.rotationDegrees(restYaw));
      }
      ItemEntityRenderer.submitMultipleFromCount(pose, event.renderCommandQueue, event.light, state, this.random, box);
      pose.popPose();
      event.cancel();
   }
}
