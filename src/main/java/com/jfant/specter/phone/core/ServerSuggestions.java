package com.jfant.specter.phone.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * The chat box's autofill, for apps: asks the server what it would suggest after a command (for example names after
 * "pay " - including players that aren't in your tab list - or your homes after "home "). Exactly the request the
 * chat box sends while you type, so the server sees nothing unusual. Results arrive on the game thread; an empty
 * list if the server suggests nothing or doesn't answer within 3 seconds.
 */
public final class ServerSuggestions {
   private ServerSuggestions() {
   }

   /** {@code command} without the slash, cursor at the end, e.g. {@code "pay Ste"}. */
   public static CompletableFuture<List<String>> complete(String command) {
      Minecraft mc = Minecraft.getInstance();
      ClientPacketListener connection = mc.getConnection();
      if (connection == null) return CompletableFuture.completedFuture(List.of());
      try {
         // Exactly what the chat box does: the text keeps its slash and parsing starts after it, so the request the
         // server gets is "/pay Ste" (proxies and plugins only treat it as a command - and suggest names from the whole
         // network - when the slash is there).
         String full = "/" + command;
         com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(full);
         reader.skip();
         CommandDispatcher<ClientSuggestionProvider> dispatcher = connection.getCommands();
         ParseResults<ClientSuggestionProvider> parse = dispatcher.parse(reader, connection.getSuggestionsProvider());
         CompletableFuture<List<String>> out = new CompletableFuture<>();
         dispatcher.getCompletionSuggestions(parse, full.length())
            .orTimeout(3, TimeUnit.SECONDS)
            .whenComplete((s, e) -> mc.execute(() -> out.complete(e != null || s == null ? List.of() : s.getList().stream().map(Suggestion::getText).toList())));
         return out;
      } catch (RuntimeException e) {
         return CompletableFuture.completedFuture(List.of());
      }
   }
}
