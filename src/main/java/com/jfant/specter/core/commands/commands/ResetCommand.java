package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.commands.arguments.ModuleArgumentType;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

public class ResetCommand extends Command {
   public ResetCommand() {
      super("reset", "Resets specified settings.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(
                  ((LiteralArgumentBuilder)literal("settings").then(argument("module", ModuleArgumentType.create()).executes(context -> {
                     Module module = (Module)context.getArgument("module", Module.class);
                     module.settings.forEach(group -> group.forEach(Setting::reset));
                     module.info("Reset all settings.");
                     return 1;
                  }))).then(literal("all").executes(context -> {
                     Modules.get().getAll().forEach(module -> module.settings.forEach(group -> group.forEach(Setting::reset)));
                     ChatUtils.infoPrefix("Modules", "Reset all module settings");
                     return 1;
                  }))
               ))
               .then(literal("gui").executes(context -> {
                  GuiThemes.get().clearWindowConfigs();
                  GuiThemes.get().settings.reset();
                  ChatUtils.info("Reset all GUI settings.");
                  return 1;
               })))
            .then(((LiteralArgumentBuilder)literal("bind").then(argument("module", ModuleArgumentType.create()).executes(context -> {
               Module module = (Module)context.getArgument("module", Module.class);
               module.keybind.reset();
               module.info("Reset bind.");
               return 1;
            }))).then(literal("all").executes(context -> {
               Modules.get().getAll().forEach(module -> module.keybind.reset());
               ChatUtils.infoPrefix("Modules", "Reset all binds.");
               return 1;
            }))))
         .then(literal("hud").executes(context -> {
            Hud.get().resetToDefaultElements();
            ChatUtils.infoPrefix("HUD", "Reset all elements.");
            return 1;
         }));
   }
}
