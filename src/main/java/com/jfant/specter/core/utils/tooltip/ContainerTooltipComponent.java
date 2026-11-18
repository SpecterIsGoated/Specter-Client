package com.jfant.specter.core.utils.tooltip;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class ContainerTooltipComponent implements ClientTooltipComponent, MeteorTooltipData {
   private static final Identifier TEXTURE_CONTAINER_BACKGROUND = SpecterCore.identifier("textures/container.png");
   private final ItemStack[] items;
   private final Color color;

   public ContainerTooltipComponent(ItemStack[] items, Color color) {
      this.items = items;
      this.color = color;
   }

   @Override
   public ClientTooltipComponent getComponent() {
      return this;
   }

   @Override
   public int getHeight(Font textRenderer) {
      return 67;
   }

   @Override
   public int getWidth(Font textRenderer) {
      return 176;
   }

   @Override
   public void renderImage(Font textRenderer, int x, int y, int width, int height, GuiGraphics context) {
      context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_CONTAINER_BACKGROUND, x, y, 0.0F, 0.0F, 176, 67, 176, 67, this.color.getPacked());
      int row = 0;
      int i = 0;

      for (ItemStack itemStack : this.items) {
         RenderUtils.drawItem(context, itemStack, x + 8 + i * 18, y + 7 + row * 18, 1.0F, true, null, false);
         if (++i >= 9) {
            i = 0;
            row++;
         }
      }
   }
}
