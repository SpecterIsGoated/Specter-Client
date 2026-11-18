package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.commands.arguments.ModuleArgumentType;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

public class BindCommand extends Command {
   public BindCommand() {
      super("bind", "Binds a specified module to the next pressed key.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(argument("module", ModuleArgumentType.create()).executes(context -> {
         Module module = (Module)context.getArgument("module", Module.class);
         Modules.get().setModuleToBind(module);
         Modules.get().awaitKeyRelease();
         module.info("Press a key to bind the module to.");
         return 1;
      }));
   }
}
