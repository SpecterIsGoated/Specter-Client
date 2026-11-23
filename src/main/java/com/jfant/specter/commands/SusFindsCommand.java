package com.jfant.specter.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.modules.SusFinds;
import com.jfant.specter.sus.SusFind;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/** .sus-finds [list|map|forget N|note N text|clear]. */
public class SusFindsCommand extends Command {
   public SusFindsCommand() {
      super("sus-finds", "Lists, maps and manages the Sus Finds list.");
   }

   @Override
   public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
      builder.executes(context -> {
         this.list(8);
         return 1;
      });
      builder.then(literal("list").executes(context -> {
         this.list(20);
         return 1;
      }));
      builder.then(literal("map").executes(context -> {
         SusFinds.openMap();
         return 1;
      }));
      builder.then(literal("forget").then(argument("id", IntegerArgumentType.integer(1)).executes(context -> {
         int id = IntegerArgumentType.getInteger(context, "id");
         SusFinds module = SusFinds.get();
         if (module.store().remove(id)) {
            module.save();
            this.info("Forgot find #%d.", new Object[]{id});
         } else {
            this.info("No find #%d.", new Object[]{id});
         }
         return 1;
      })));
      builder.then(literal("note").then(argument("id", IntegerArgumentType.integer(1)).then(argument("text", StringArgumentType.greedyString()).executes(context -> {
         int id = IntegerArgumentType.getInteger(context, "id");
         SusFind f = SusFinds.get().store().byId(id);
         if (f == null) {
            this.info("No find #%d.", new Object[]{id});
         } else {
            f.note = StringArgumentType.getString(context, "text").trim();
            SusFinds.get().save();
            this.info("Noted on #%d.", new Object[]{id});
         }
         return 1;
      }))));
      builder.then(literal("clear").executes(context -> {
         SusFinds module = SusFinds.get();
         int n = module.store().size();
         module.store().clear();
         module.save();
         this.info("Forgot all %d finds here.", new Object[]{n});
         return 1;
      }));
   }

   private void list(int max) {
      SusFinds module = SusFinds.get();
      List<SusFind> all = module.sorted();
      if (all.isEmpty()) {
         this.info("No finds here yet. Turn on Sus Finds with Sus Chunks and/or Sus Chunks V2.", new Object[0]);
         return;
      }
      this.info("%d finds (Confirmed first, then nearest):", new Object[]{all.size()});
      for (int i = 0; i < Math.min(max, all.size()); i++) {
         SusFind f = all.get(i);
         String note = f.note.isEmpty() ? "" : " - " + f.note;
         this.info("  #%d %s at %d, %d - %d chunks, %s%s", new Object[]{f.id, f.confidence().title, f.blockX(), f.blockZ(), f.size(), f.sourceText(), note});
      }
   }
}
