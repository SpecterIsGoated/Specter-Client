package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.pvp.PvpCard;
import com.jfant.specter.pvp.Targets;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class OpponentStats extends Module {
   private static final int TOTEM_POP = 35;
   private static final int DEATH = 3;
   private static final double TARGET_RANGE = 24.0;
   private static final int WIDTH = 180;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final Setting<Boolean> hideWithoutTarget = this.sgGeneral
      .add(new BoolSetting.Builder().name("hide-without-target").description("Leave the card out while nobody is in range.").defaultValue(true).build());
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.TopCenter, 0, 3);
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
   private final Map<UUID, Integer> pops = new HashMap<>();

   public OpponentStats() {
      super(SpecterClientAddon.HUD, "opponent-stats", "A card for the player you are fighting: their health, totem pops, armour and how far away they are.");
   }

   @Override
   public void onActivate() {
      this.pops.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.pops.clear();
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      Targets.hit(event.entity);
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundEntityEventPacket packet) {
         byte status = packet.getEventId();
         if (status == 35 || status == 3) {
            this.mc.execute(() -> {
               if (this.mc.level != null && packet.getEntity(this.mc.level) instanceof Player player) {
                  if (status == 35) {
                     this.pops.merge(player.getUUID(), 1, Integer::sum);
                  } else {
                     this.pops.remove(player.getUUID());
                  }
               }
            });
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (PvpCard.visible()) {
         Player target = Targets.current(this.mc.player.position(), 24.0);
         if (target != null || !this.hideWithoutTarget.get()) {
            this.draw(event.drawContext, target);
         }
      }
   }

   private void draw(GuiGraphics ctx, Player target) {
      int inner = 170;
      if (target == null) {
         PvpCard.begin(ctx, this.spot, this.scale.get(), 180, 9);
         CardText.draw(ctx, "No target", 0.0F, 0.0F, -4149795, true);
         PvpCard.end(ctx);
      } else {
         PvpCard.begin(ctx, this.spot, this.scale.get(), 180, 32);
         PlayerFaceRenderer.draw(ctx, target instanceof AbstractClientPlayer player ? player.getSkin() : DefaultPlayerSkin.getDefaultSkin(), 0, 0, 9, -1);
         String distance = String.format("%.1fm", this.mc.player.distanceTo(target));
         int popped = this.pops.getOrDefault(target.getUUID(), 0);
         String popText = popped + (popped == 1 ? " POP" : " POPS");
         int badgeLeft = popped > 0 ? inner - PvpCard.badgeWidth(popText) - 4 : inner;
         FormattedCharSequence name = CardText.clip(Component.literal(target.getName().getString()), badgeLeft - 12 - CardText.width(distance) - 4);
         CardText.draw(ctx, name, 12.0F, 0.0F, -1, true);
         CardText.draw(ctx, distance, (float)(12 + CardText.width(name) + 4), 0.0F, -4149795, true);
         if (popped > 0) {
            PvpCard.badge(ctx, popText, inner, 0, -22016, -15069440);
         }

         PvpCard.healthRow(ctx, target, 12, inner);
         int slot = inner / PvpCard.ARMOUR.length;

         for (int i = 0; i < PvpCard.ARMOUR.length; i++) {
            ItemStack stack = target.getItemBySlot(PvpCard.ARMOUR[i]);
            int x = i * slot;
            int y = 24;
            if (stack.isEmpty()) {
               CardText.draw(ctx, "-", (float)(x + 2), (float)y, -4149795, true);
            } else {
               PvpCard.item(ctx, stack, x, y);
               int left = PvpCard.durability(stack);
               if (left >= 0) {
                  CardText.draw(ctx, left + "%", (float)(x + 8 + 3), (float)y, PvpCard.durabilityText(left), true);
               }
            }
         }

         PvpCard.end(ctx);
      }
   }
}
