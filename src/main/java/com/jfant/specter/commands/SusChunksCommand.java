package com.jfant.specter.commands;

import com.jfant.specter.ScoreStore;
import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.modules.SusChunks;
import com.jfant.specter.scan.Indicator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class SusChunksCommand extends Command {
   public SusChunksCommand() {
      super("sus-chunks", "Inspects and manages what Sus Chunks has found.");
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
      builder.then(((LiteralArgumentBuilder)literal("top").executes(context -> {
         this.top(5);
         return 1;
      })).then(argument("count", IntegerArgumentType.integer(1, 20)).executes(context -> {
         this.top(IntegerArgumentType.getInteger(context, "count"));
         return 1;
      })));
      builder.then(literal("rescan").executes(context -> {
         SusChunks module = this.module();
         module.rescanLoadedChunks();
         this.info("Rescanning all loaded chunks.", new Object[0]);
         return 1;
      }));
      builder.then(literal("clear").executes(context -> {
         this.module().reset();
         this.info("Cleared all tracked chunks for this session.", new Object[0]);
         return 1;
      }));
      builder.then(literal("save").executes(context -> {
         SusChunks module = this.module();
         int saved = module.save();
         if (saved < 0) {
            this.info("Nothing to save - join a world first.", new Object[0]);
         } else {
            this.info("Saving %d chunks to %s in the background.", new Object[]{saved, module.file().getName()});
         }

         return 1;
      }));
      builder.then(literal("reload").executes(context -> {
         if (!this.module().load(true)) {
            this.info("Nothing to load - join a world first.", new Object[0]);
         } else {
            this.info("Reloading chunk data in the background.", new Object[0]);
         }

         return 1;
      }));
   }

   private void stats() {
      SusChunks module = this.module();
      if (module.scope() == null) {
         this.info("Not tracking anything yet - join a world with the module enabled.", new Object[0]);
      } else {
         this.info("Tracking %s:", new Object[]{module.scope()});
         this.info("  chunks scanned: %d", new Object[]{module.store().size()});
         this.info("  sim distance: %d chunks", new Object[]{module.baseRadius()});
         this.info("  triangulation sources: %d, making %d areas", new Object[]{module.sourceCount(), module.clusterCount()});
         if (module.discountingTrialChambers()) {
            this.info("  ignored near trial chambers: %d chunks", new Object[]{module.store().discountedChunks()});
         }

         if (module.discountingShafts()) {
            this.info("  ignored near shafts: %d chunks", new Object[]{module.store().shaftDiscountedChunks()});
         }

         this.info(
            "  overlay (certainty %.1f = score >= %.1f, plus candidate areas): %d chunks",
            new Object[]{module.certainty(), module.target(), module.flaggedCount()}
         );
         this.info("  on screen: %d chunks in %d boxes (limit %d)", new Object[]{module.drawnCells(), module.drawnBoxes(), module.renderBudget()});
      }
   }

   private void top(int n) {
      SusChunks module = this.module();
      List<ScoreStore.Entry> entries = module.store().top(n, module.scoringConfig());
      if (entries.isEmpty()) {
         this.info("No chunks with a non-zero score yet.", new Object[0]);
      } else {
         this.info("Top %d chunks by score (certainty %.1f = score %.1f):", new Object[]{entries.size(), module.certainty(), module.target()});

         for (ScoreStore.Entry entry : entries) {
            StringBuilder breakdown = new StringBuilder();

            for (Indicator indicator : Indicator.VALUES) {
               if (!indicator.hidden && module.indicatorEnabled(indicator)) {
                  int ordinal = indicator.ordinal();
                  int count = ordinal < entry.counts().length ? entry.counts()[ordinal] : 0;
                  if (count != 0) {
                     if (breakdown.length() > 0) {
                        breakdown.append(", ");
                     }

                     breakdown.append(indicator.csvName).append('x').append(count);
                     if (entry.effective()[ordinal] == 0) {
                        breakdown.append(vetoReason(indicator));
                     } else if (indicator == Indicator.HIDDEN_GLOW) {
                        breakdown.append(" (").append(entry.effective()[ordinal]).append(" in the geode)");
                     }
                  }
               }
            }

            this.info("  (%d, %d) score %.1f: %s", new Object[]{entry.chunkX() << 4, entry.chunkZ() << 4, entry.score(), breakdown.toString()});
         }
      }
   }

   private static String vetoReason(Indicator indicator) {
      return switch (indicator) {
         case SCULK -> " (ignored: deep dark nearby)";
         case AMETHYST_CLUSTER -> " (ignored: partial buds seen)";
         default -> " (ignored)";
      };
   }

   private SusChunks module() {
      return Modules.get().get(SusChunks.class);
   }
}
