package com.jfant.specter.core.utils.render;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.render.color.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;

public class PeekScreen extends ShulkerBoxScreen {
   private final Identifier TEXTURE = Identifier.parse("textures/gui/container/shulker_box.png");
   private final ItemStack storageBlock;

   public PeekScreen(ItemStack storageBlock, ItemStack[] contents) {
      super(
         new ShulkerBoxMenu(0, SpecterCore.mc.player.getInventory(), new SimpleContainer(contents)),
         SpecterCore.mc.player.getInventory(),
         storageBlock.getHoverName()
      );
      this.storageBlock = storageBlock;
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      return false;
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (input.key() != 256 && !SpecterCore.mc.options.keyInventory.matches(input)) {
         return false;
      } else {
         this.onClose();
         return true;
      }
   }

   @Override
   protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
      Color color = Utils.getShulkerColor(this.storageBlock);
      int i = (this.width - this.imageWidth) / 2;
      int j = (this.height - this.imageHeight) / 2;
      context.blit(
         RenderPipelines.GUI_TEXTURED,
         this.TEXTURE,
         i,
         j,
         0.0F,
         0.0F,
         this.imageWidth,
         this.imageHeight,
         this.imageWidth,
         this.imageHeight,
         256,
         256,
         ARGB.colorFromFloat(color.a / 255.0F, color.r / 255.0F, color.g / 255.0F, color.b / 255.0F)
      );
   }
}
