package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.ShowText;

public class BindsCommand extends Command {
   public BindsCommand() {
      super("binds", "List of all bound modules.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         List<Module> modules = Modules.get().getAll().stream().filter(modulex -> modulex.keybind.isSet()).toList();
         ChatUtils.info("--- Bound Modules ((highlight)%d(default)) ---", modules.size());

         for (Module module : modules) {
            HoverEvent hoverEvent = new ShowText(this.getTooltip(module));
            MutableComponent text = Component.literal(module.title).withStyle(ChatFormatting.WHITE);
            text.setStyle(text.getStyle().withHoverEvent(hoverEvent));
            MutableComponent sep = Component.literal(" - ");
            sep.setStyle(sep.getStyle().withHoverEvent(hoverEvent));
            text.append(sep.withStyle(ChatFormatting.GRAY));
            MutableComponent key = Component.literal(module.keybind.toString());
            key.setStyle(key.getStyle().withHoverEvent(hoverEvent));
            text.append(key.withStyle(ChatFormatting.GRAY));
            ChatUtils.sendMsg(text);
         }

         return 1;
      });
   }

   private MutableComponent getTooltip(Module module) {
      MutableComponent tooltip = Component.literal(Utils.nameToTitle(module.title)).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD).append("\n\n");
      tooltip.append(Component.literal(module.description).withStyle(ChatFormatting.WHITE));
      return tooltip;
   }
}
