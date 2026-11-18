package com.jfant.specter.core.utils.render;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;

public record CustomBannerGuiElementRenderState(
   BannerFlagModel flag,
   DyeColor baseColor,
   BannerPatternLayers resultBannerPatterns,
   int x1,
   int y1,
   int x2,
   int y2,
   @Nullable ScreenRectangle scissorArea,
   @Nullable ScreenRectangle bounds,
   float scale
) implements PictureInPictureRenderState {
   public CustomBannerGuiElementRenderState(
      BannerFlagModel bannerFlagBlockModel,
      DyeColor color,
      BannerPatternLayers bannerPatterns,
      int x1,
      int y1,
      int x2,
      int y2,
      @Nullable ScreenRectangle scissorArea,
      float scale
   ) {
      this(bannerFlagBlockModel, color, bannerPatterns, x1, y1, x2, y2, scissorArea, PictureInPictureRenderState.getBounds(x1, y1, x2, y2, scissorArea), scale);
   }

   @Override
   public int x0() {
      return this.x1;
   }

   @Override
   public int y0() {
      return this.y1;
   }

   @Override
   public int x1() {
      return this.x2;
   }

   @Override
   public int y1() {
      return this.y2;
   }
}
