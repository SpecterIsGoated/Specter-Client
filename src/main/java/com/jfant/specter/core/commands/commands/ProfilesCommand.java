package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.commands.arguments.ProfileArgumentType;
import com.jfant.specter.core.systems.profiles.Profile;
import com.jfant.specter.core.systems.profiles.Profiles;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

public class ProfilesCommand extends Command {
   public ProfilesCommand() {
      super("profiles", "Loads and saves profiles.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("load").then(argument("profile", ProfileArgumentType.create()).executes(context -> {
         Profile profile = ProfileArgumentType.get(context);
         if (profile != null) {
            profile.load();
            this.info("Loaded profile (highlight)%s(default).", new Object[]{profile.name.get()});
         }

         return 1;
      })));
      builder.then(literal("save").then(argument("profile", ProfileArgumentType.create()).executes(context -> {
         Profile profile = ProfileArgumentType.get(context);
         if (profile != null) {
            profile.save();
            this.info("Saved profile (highlight)%s(default).", new Object[]{profile.name.get()});
         }

         return 1;
      })));
      builder.then(literal("delete").then(argument("profile", ProfileArgumentType.create()).executes(context -> {
         Profile profile = ProfileArgumentType.get(context);
         if (profile != null) {
            Profiles.get().remove(profile);
            this.info("Deleted profile (highlight)%s(default).", new Object[]{profile.name.get()});
         }

         return 1;
      })));
   }
}
