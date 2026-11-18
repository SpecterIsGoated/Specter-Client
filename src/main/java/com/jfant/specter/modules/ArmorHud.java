package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.pvp.PvpCard;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public class ArmorHud extends Module {
   private static final int WIDTH = 74;
   private static final int ROW = 10;
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.BottomCenter, -123 - PvpCard.startWidth(100) - 3 - PvpCard.startWidth(74) / 2, 2);
   private final Setting<Double> scale = this.sgPosition
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the card. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .build()
      );

   public ArmorHud() {
      super(SpecterClientAddon.HUD, "armor-hud", "A card with how worn each piece of your armour is.");
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (PvpCard.visible()) {
         this.draw(event.graphics);
      }
   }

   private void draw(GuiGraphicsExtractor ctx) {
      int inner = 64;
      PvpCard.begin(ctx, this.spot, this.scale.get(), 74, 12 + PvpCard.ARMOUR.length * 10 - 2);
      CardText.draw(ctx, "Armour", 0.0F, 0.0F, -1065473, true);
      int numberWidth = CardText.width("100");

      for (int i = 0; i < PvpCard.ARMOUR.length; i++) {
         ItemStack stack = this.mc.player.getItemBySlot(PvpCard.ARMOUR[i]);
         int y = 12 + i * 10;
         if (stack.isEmpty()) {
            CardText.draw(ctx, "-", 2.0F, (float)y, -4149795, true);
         } else {
            PvpCard.item(ctx, stack, 0, y);
            int left = PvpCard.durability(stack);
            if (left >= 0) {
               int barLeft = 11;
               int barRight = inner - numberWidth - 3;
               ctx.fill(barLeft, y + 3, barRight, y + 6, -1945500652);
               ctx.fill(barLeft, y + 3, barLeft + Math.round((barRight - barLeft) * left / 100.0F), y + 6, PvpCard.durabilityBar(left));
               String text = String.valueOf(left);
               CardText.draw(ctx, text, (float)(inner - CardText.width(text)), (float)y, PvpCard.durabilityText(left), true);
            }
         }
      }

      PvpCard.end(ctx);
   }
}
