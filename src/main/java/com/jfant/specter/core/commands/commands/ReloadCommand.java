package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.renderer.Fonts;
import com.jfant.specter.core.systems.Systems;
import com.jfant.specter.core.systems.friends.Friend;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

public class ReloadCommand extends Command {
   public ReloadCommand() {
      super("reload", "Reloads many systems.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         this.warning("Reloading systems, this may take a while.", new Object[0]);
         Systems.load();
         Fonts.refresh();
         MeteorExecutor.execute(() -> Friends.get().forEach(Friend::updateInfo));
         return 1;
      });
   }
}
