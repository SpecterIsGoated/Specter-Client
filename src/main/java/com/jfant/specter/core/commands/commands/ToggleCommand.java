package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.commands.arguments.ModuleArgumentType;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.ArrayList;
import net.minecraft.commands.SharedSuggestionProvider;

public class ToggleCommand extends Command {
   public ToggleCommand() {
      super("toggle", "Toggles a module.", "t");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(((LiteralArgumentBuilder)literal("all").then(literal("on").executes(context -> {
         new ArrayList<>(Modules.get().getAll()).forEach(Module::enable);
         Hud.get().active = true;
         return 1;
      }))).then(literal("off").executes(context -> {
         new ArrayList<>(Modules.get().getActive()).forEach(Module::toggle);
         Hud.get().active = false;
         return 1;
      })))).then(((RequiredArgumentBuilder)((RequiredArgumentBuilder)argument("module", ModuleArgumentType.create()).executes(context -> {
         Module m = ModuleArgumentType.get(context);
         m.toggle();
         m.sendToggledMsg();
         return 1;
      })).then(literal("on").executes(context -> {
         Module m = ModuleArgumentType.get(context);
         m.enable();
         return 1;
      }))).then(literal("off").executes(context -> {
         Module m = ModuleArgumentType.get(context);
         m.disable();
         return 1;
      })))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal("hud").executes(context -> {
         Hud.get().active = !Hud.get().active;
         return 1;
      })).then(literal("on").executes(context -> {
         Hud.get().active = true;
         return 1;
      }))).then(literal("off").executes(context -> {
         Hud.get().active = false;
         return 1;
      })));
   }
}
