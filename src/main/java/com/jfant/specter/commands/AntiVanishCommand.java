package com.jfant.specter.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.modules.AntiVanish;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.entity.player.PlayerSkin;

public class AntiVanishCommand extends Command {
   public AntiVanishCommand() {
      super("anti-vanish", "Shows what the Anti Vanish \"Is Watching You\" pop-up looks like.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         this.preview(null);
         return 1;
      });
      builder.then(
         ((LiteralArgumentBuilder)literal("preview").executes(context -> {
               this.preview(null);
               return 1;
            }))
            .then(
               argument("name", StringArgumentType.word())
                  .suggests(
                     (context, suggestions) -> SharedSuggestionProvider.suggest(
                        mc.getConnection() == null ? List.of() : mc.getConnection().getOnlinePlayers().stream().map(e -> e.getProfile().name()).toList(),
                        suggestions
                     )
                  )
                  .executes(context -> {
                     this.preview(StringArgumentType.getString(context, "name"));
                     return 1;
                  })
            )
      );
   }

   private void preview(String name) {
      if (mc.player == null) {
         this.error("Join a world first.", new Object[0]);
      } else {
         AntiVanish module = Modules.get().get(AntiVanish.class);
         if (!module.isActive()) {
            this.error("Turn anti-vanish on first.", new Object[0]);
         } else if (name == null) {
            module.previewAlert(mc.player.getName().getString(), mc.player.getSkin());
            this.info("Showing the pop-up with your own name and head.", new Object[0]);
         } else {
            PlayerInfo entry = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(name);
            PlayerSkin skin = entry != null ? entry.getSkin() : null;
            module.previewAlert(entry != null ? entry.getProfile().name() : name, skin);
            this.info("Showing the pop-up for " + name + (entry != null ? "." : " (not on this server, so a default head)."), new Object[0]);
         }
      }
   }
}
