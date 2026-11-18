package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.PvpCard;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TotemCounter extends Module {
   static final int WIDTH = 100;
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.BottomCenter, -123 - PvpCard.startWidth(100) / 2, 2);
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

   public TotemCounter() {
      super(SpecterClientAddon.HUD, "totem-counter", "A card with your health, the totems you carry and whether one is in your offhand.");
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (PvpCard.visible()) {
         this.draw(event.graphics);
      }
   }

   private void draw(GuiGraphicsExtractor ctx) {
      int inner = 90;
      PvpCard.begin(ctx, this.spot, this.scale.get(), 100, 33);
      CardText.draw(ctx, "You", 0.0F, 0.0F, -1065473, true);
      PvpCard.healthText(ctx, this.mc.player, inner, 0);
      PvpCard.bar(ctx, 0, 12, inner, this.mc.player);
      int y = 21;
      int totems = Hands.count(Items.TOTEM_OF_UNDYING);
      PvpCard.item(ctx, new ItemStack(Items.TOTEM_OF_UNDYING), 0, y);
      CardText.draw(ctx, String.valueOf(totems), 11.0F, (float)y, totems == 0 ? -43691 : (totems <= 2 ? -22016 : -1), true);
      boolean offhand = this.mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
      HumanTotem humanTotem = Modules.get().get(HumanTotem.class);
      String status = humanTotem != null && humanTotem.isActive() ? humanTotem.statusText() : (offhand ? "Offhand" : "No offhand");
      CardText.draw(ctx, status, (float)(inner - CardText.width(status)), (float)y, offhand ? -9705867 : -22016, true);
      PvpCard.end(ctx);
   }
}
