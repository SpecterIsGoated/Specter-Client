package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.commands.Commands;
import com.jfant.specter.core.commands.arguments.CommandArgumentType;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class HelpCommand extends Command {
   public HelpCommand() {
      super("help", "Shows you what a command does.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(argument("command", CommandArgumentType.create()).executes(context -> {
         this.showHelp(CommandArgumentType.get(context));
         return 1;
      }));
      builder.executes(context -> {
         this.showHelp(this);
         return 1;
      });
   }

   private void showHelp(Command cmd) {
      MutableComponent msg = Component.literal("");
      msg.append(Component.literal("Help for ").withStyle(ChatFormatting.GRAY).append(Component.literal(cmd.getName()).withStyle(ChatFormatting.YELLOW)));
      msg.append(Component.literal("\n "))
         .append(
            Component.literal("Description: ").withStyle(ChatFormatting.GRAY).append(Component.literal(cmd.getDescription()).withStyle(ChatFormatting.WHITE))
         );
      if (!cmd.getAliases().isEmpty()) {
         msg.append(Component.literal("\n ")).append(Component.literal("Aliases: ").withStyle(ChatFormatting.GRAY));
         msg.append(Component.literal(String.join(", ", cmd.getAliases())).withStyle(ChatFormatting.AQUA));
      }

      msg.append(this.getUsageText(cmd));
      ChatUtils.sendMsg(msg);
   }

   private MutableComponent getUsageText(Command cmd) {
      SharedSuggestionProvider source = mc.getConnection().getSuggestionsProvider();
      CommandNode<SharedSuggestionProvider> root = Commands.DISPATCHER.getRoot();
      CommandNode<SharedSuggestionProvider> node = root.getChild(cmd.getName());
      MutableComponent usagesText = Component.literal("");
      if (node != null) {
         Map<CommandNode<SharedSuggestionProvider>, String> usages = Commands.DISPATCHER.getSmartUsage(node, source);

         for (String usage : usages.values()) {
            usagesText.append(Component.literal("\n " + cmd + " ").withStyle(ChatFormatting.GREEN))
               .append(Component.literal(usage).withStyle(ChatFormatting.GREEN));
         }
      }

      if (usagesText.getString().isEmpty()) {
         usagesText.append(Component.literal("\n " + cmd).withStyle(ChatFormatting.GREEN));
      }

      return Component.literal("\n Usage:").withStyle(ChatFormatting.GRAY).append(usagesText);
   }
}
