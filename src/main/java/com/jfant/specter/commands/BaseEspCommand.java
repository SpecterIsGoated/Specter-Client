package com.jfant.specter.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.modules.BaseEsp;
import com.jfant.specter.reveal.RevealStore;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Map.Entry;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

public class BaseEspCommand extends Command {
   public BaseEspCommand() {
      super("base-esp", "Inspects and manages the Base Esp map.");
   }

   @Override
   public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
      builder.executes(context -> {
         this.stats();
         return 1;
      });
      builder.then(literal("stats").executes(context -> {
         this.stats();
         return 1;
      }));
      builder.then(literal("clear").executes(context -> {
         this.module().reset();
         this.info("Cleared the base map for this session.", new Object[0]);
         return 1;
      }));
      builder.then(literal("save").executes(context -> {
         BaseEsp module = this.module();
         int saved = module.save();
         if (saved < 0) {
            this.info("Nothing to save - join a world first.", new Object[0]);
         } else {
            this.info("Saved %d entries to %s.", new Object[]{saved, module.file().getName()});
         }

         return 1;
      }));
      builder.then(literal("reload").executes(context -> {
         int loaded = this.module().load();
         if (loaded < 0) {
            this.info("Nothing to load - join a world first.", new Object[0]);
         } else {
            this.info("Merged %d entries from disk.", new Object[]{loaded});
         }

         return 1;
      }));
   }

   private void stats() {
      BaseEsp module = this.module();
      if (module.scope() == null) {
         this.info("Not tracking anything yet - join a world with the module enabled.", new Object[0]);
      } else {
         this.info(
            "Base map for %s: %d blocks, %d item frames, %d armor stands",
            new Object[]{
               module.scope(),
               module.store().size(),
               module.store().markerCount(RevealStore.Marker.ITEM_FRAME),
               module.store().markerCount(RevealStore.Marker.ARMOR_STAND)
            }
         );
         module.store()
            .breakdown()
            .entrySet()
            .stream()
            .sorted(Entry.<Block, Integer>comparingByValue().reversed())
            .limit(12L)
            .forEach(entry -> this.info("  %s: %d", new Object[]{BuiltInRegistries.BLOCK.getKey(entry.getKey()), entry.getValue()}));
      }
   }

   private BaseEsp module() {
      return Modules.get().get(BaseEsp.class);
   }
}
