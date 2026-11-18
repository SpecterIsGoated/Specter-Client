package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.ShowText;

public class ModulesCommand extends Command {
   public ModulesCommand() {
      super("modules", "Displays a list of all modules.", "features");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         ChatUtils.info("--- Modules ((highlight)%d(default)) ---", Modules.get().getCount());
         Modules.loopCategories().forEach(category -> {
            MutableComponent categoryMessage = Component.literal("");
            Modules.get().getGroup(category).forEach(module -> categoryMessage.append(this.getModuleText(module)));
            ChatUtils.sendMsg(category.name, categoryMessage);
         });
         return 1;
      });
   }

   private MutableComponent getModuleText(Module module) {
      MutableComponent tooltip = Component.literal("");
      tooltip.append(Component.literal(module.title).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD)).append("\n");
      tooltip.append(Component.literal(module.name).withStyle(ChatFormatting.GRAY)).append("\n\n");
      tooltip.append(Component.literal(module.description).withStyle(ChatFormatting.WHITE));
      MutableComponent finalModule = Component.literal(module.title);
      if (!module.isActive()) {
         finalModule.withStyle(ChatFormatting.GRAY);
      }

      if (!module.equals(Modules.get().getGroup(module.category).getLast())) {
         finalModule.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
      }

      finalModule.setStyle(finalModule.getStyle().withHoverEvent(new ShowText(tooltip)));
      return finalModule;
   }
}
