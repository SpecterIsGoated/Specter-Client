package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.commands.Commands;
import com.jfant.specter.core.systems.config.Config;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CommandSuggestions.class})
public abstract class ChatInputSuggestorMixin {
   @Shadow
   private ParseResults<SharedSuggestionProvider> currentParse;
   @Shadow
   @Final
   EditBox input;
   @Shadow
   boolean keepSuggestions;
   @Shadow
   private CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow
   private SuggestionsList suggestions;

   @Shadow
   protected abstract void updateUsageInfo();

   @Inject(
      method = {"updateCommandInfo"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
         remap = false
      )},
      cancellable = true
   )
   public void onRefresh(CallbackInfo ci, @Local StringReader reader) {
      String prefix = Config.get().prefix.get();
      int length = prefix.length();
      if (reader.canRead(length) && reader.getString().startsWith(prefix, reader.getCursor())) {
         reader.setCursor(reader.getCursor() + length);
         if (this.currentParse == null) {
            this.currentParse = Commands.DISPATCHER.parse(reader, SpecterCore.mc.getConnection().getSuggestionsProvider());
         }

         int cursor = this.input.getCursorPosition();
         if (cursor >= length && (this.suggestions == null || !this.keepSuggestions)) {
            this.pendingSuggestions = Commands.DISPATCHER.getCompletionSuggestions(this.currentParse, cursor);
            this.pendingSuggestions.thenRun(() -> {
               if (this.pendingSuggestions.isDone()) {
                  this.updateUsageInfo();
               }
            });
         }

         ci.cancel();
      }
   }
}
