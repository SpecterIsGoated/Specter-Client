package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.commands.Commands;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;

public class CommandsCommand extends Command {
   public CommandsCommand() {
      super("commands", "List of all commands.", "help");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         ChatUtils.info("--- Commands ((highlight)%d(default)) ---", Commands.COMMANDS.size());
         MutableComponent commands = Component.literal("");
         Commands.COMMANDS.forEach(command -> commands.append(this.getCommandText(command)));
         ChatUtils.sendMsg(commands);
         return 1;
      });
   }

   private MutableComponent getCommandText(Command command) {
      MutableComponent tooltip = Component.literal("");
      tooltip.append(Component.literal(Utils.nameToTitle(command.getName())).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD)).append("\n");
      MutableComponent aliases = Component.literal(Config.get().prefix.get() + command.getName());
      if (!command.getAliases().isEmpty()) {
         aliases.append(", ");

         for (String alias : command.getAliases()) {
            if (!alias.isEmpty()) {
               aliases.append(Config.get().prefix.get() + alias);
               if (!alias.equals(command.getAliases().getLast())) {
                  aliases.append(", ");
               }
            }
         }
      }

      tooltip.append(aliases.withStyle(ChatFormatting.GRAY)).append("\n\n");
      tooltip.append(Component.literal(command.getDescription()).withStyle(ChatFormatting.WHITE));
      MutableComponent text = Component.literal(Utils.nameToTitle(command.getName()));
      if (command != Commands.COMMANDS.getLast()) {
         text.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
      }

      text.setStyle(text.getStyle().withHoverEvent(new ShowText(tooltip)).withClickEvent(new SuggestCommand(Config.get().prefix.get() + command.getName())));
      return text;
   }
}
