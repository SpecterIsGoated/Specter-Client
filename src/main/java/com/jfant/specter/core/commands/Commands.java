package com.jfant.specter.core.commands;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.commands.commands.BindCommand;
import com.jfant.specter.core.commands.commands.BindsCommand;
import com.jfant.specter.core.commands.commands.CommandsCommand;
import com.jfant.specter.core.commands.commands.FriendsCommand;
import com.jfant.specter.core.commands.commands.HelpCommand;
import com.jfant.specter.core.commands.commands.ModulesCommand;
import com.jfant.specter.core.commands.commands.ProfilesCommand;
import com.jfant.specter.core.commands.commands.ReloadCommand;
import com.jfant.specter.core.commands.commands.ResetCommand;
import com.jfant.specter.core.commands.commands.SettingCommand;
import com.jfant.specter.core.commands.commands.ToggleCommand;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.pathing.PathManagers;
import com.jfant.specter.core.utils.PostInit;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;

public class Commands {
   public static final List<Command> COMMANDS = new ArrayList<>();
   public static CommandDispatcher<SharedSuggestionProvider> DISPATCHER = new CommandDispatcher();

   @PostInit(
      dependencies = {PathManagers.class}
   )
   public static void init() {
      add(new FriendsCommand());
      add(new CommandsCommand());
      add(new ProfilesCommand());
      add(new ReloadCommand());
      add(new ResetCommand());
      add(new ToggleCommand());
      add(new SettingCommand());
      add(new ModulesCommand());
      add(new BindsCommand());
      add(new BindCommand());
      add(new HelpCommand());
      COMMANDS.sort(Comparator.comparing(Command::getName));
      SpecterCore.EVENT_BUS.subscribe(Commands.class);
   }

   public static void add(Command command) {
      COMMANDS.removeIf(existing -> existing.getName().equals(command.getName()));
      COMMANDS.add(command);
   }

   public static void dispatch(String message) throws CommandSyntaxException {
      DISPATCHER.execute(message, SpecterCore.mc.getConnection().getSuggestionsProvider());
   }

   public static Command get(String name) {
      for (Command command : COMMANDS) {
         if (command.getName().equals(name)) {
            return command;
         }
      }

      return null;
   }

   @EventHandler
   private static void onJoin(GameJoinedEvent event) {
      ClientPacketListener networkHandler = SpecterCore.mc.getConnection();
      Command.REGISTRY_ACCESS = CommandBuildContext.simple(networkHandler.registryAccess(), networkHandler.enabledFeatures());
      DISPATCHER = new CommandDispatcher();

      for (Command command : COMMANDS) {
         command.registerTo(DISPATCHER);
      }
   }
}
