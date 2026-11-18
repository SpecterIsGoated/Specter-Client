package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.commands.arguments.ModuleArgumentType;
import com.jfant.specter.core.commands.arguments.SettingArgumentType;
import com.jfant.specter.core.commands.arguments.SettingValueArgumentType;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.gui.tabs.TabScreen;
import com.jfant.specter.core.gui.tabs.Tabs;
import com.jfant.specter.core.gui.tabs.builtin.ConfigTab;
import com.jfant.specter.core.gui.tabs.builtin.HudTab;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

public class SettingCommand extends Command {
   public SettingCommand() {
      super("settings", "Allows you to view and change module settings.", "s");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("hud").executes(context -> {
         TabScreen screen = Tabs.get(HudTab.class).createScreen(GuiThemes.get());
         screen.parent = null;
         Utils.screenToOpen = screen;
         return 1;
      }));
      builder.then(literal("config").executes(context -> {
         TabScreen screen = Tabs.get(ConfigTab.class).createScreen(GuiThemes.get());
         screen.parent = null;
         Utils.screenToOpen = screen;
         return 1;
      }));
      builder.then(
         literal("config")
            .then(
               ((RequiredArgumentBuilder)argument("setting", SettingArgumentType.create()).executes(context -> {
                     Setting<?> setting = SettingArgumentType.get(context, Config.get().settings);
                     ChatUtils.infoPrefix("Config", "Setting (highlight)%s(default) is (highlight)%s(default).", setting.title, setting.get());
                     return 1;
                  }))
                  .suggests((ctx, suggestionsBuilder) -> SettingArgumentType.listSuggestions(suggestionsBuilder, Config.get().settings))
                  .then(((RequiredArgumentBuilder)argument("value", SettingValueArgumentType.create()).executes(context -> {
                     Setting<?> setting = SettingArgumentType.get(context, Config.get().settings);
                     String value = SettingValueArgumentType.get(context);
                     if (setting.parse(value)) {
                        ChatUtils.infoPrefix("Config", "Setting (highlight)%s(default) changed to (highlight)%s(default).", setting.title, value);
                     }

                     return 1;
                  })).suggests((context, suggestionsBuilder) -> SettingValueArgumentType.listSuggestions(context, suggestionsBuilder, Config.get().settings)))
            )
      );
      builder.then(argument("module", ModuleArgumentType.create()).executes(context -> {
         Module module = (Module)context.getArgument("module", Module.class);
         WidgetScreen screen = GuiThemes.get().moduleScreen(module);
         screen.parent = null;
         Utils.screenToOpen = screen;
         return 1;
      }));
      builder.then(
         argument("module", ModuleArgumentType.create()).then(((RequiredArgumentBuilder)argument("setting", SettingArgumentType.create()).executes(context -> {
            Setting<?> setting = SettingArgumentType.get(context);
            ModuleArgumentType.get(context).info("Setting (highlight)%s(default) is (highlight)%s(default).", setting.title, setting.get());
            return 1;
         })).then(argument("value", SettingValueArgumentType.create()).executes(context -> {
            Setting<?> setting = SettingArgumentType.get(context);
            String value = SettingValueArgumentType.get(context);
            if (setting.parse(value)) {
               ModuleArgumentType.get(context).info("Setting (highlight)%s(default) changed to (highlight)%s(default).", setting.title, value);
            }

            return 1;
         })))
      );
   }
}
