package com.jfant.specter.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.modules.TimeSet;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.commands.SharedSuggestionProvider;

public class TimeSetCommand extends Command {
   public TimeSetCommand() {
      super("timeset", "Saves, loads and sets the time of day Time Set shows.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("save").then(argument("name", StringArgumentType.greedyString()).executes(context -> {
         String name = StringArgumentType.getString(context, "name");
         int ticks = this.module().save(name);
         this.info("Saved %s as %d (%s).", new Object[]{name.trim(), ticks, TimeSet.clock(ticks)});
         return 1;
      })));
      builder.then(literal("load").then(argument("name", StringArgumentType.greedyString()).executes(context -> {
         String name = StringArgumentType.getString(context, "name");
         if (!this.module().load(name)) {
            this.info("No saved time called %s.", new Object[]{name.trim()});
         } else {
            this.info("Showing saved time %s.", new Object[]{name.trim()});
            this.offNote();
         }

         return 1;
      })));
      builder.then(literal("delete").then(argument("name", StringArgumentType.greedyString()).executes(context -> {
         String name = StringArgumentType.getString(context, "name");
         if (this.module().delete(name)) {
            this.info("Deleted %s.", new Object[]{name.trim()});
         } else {
            this.info("No saved time called %s.", new Object[]{name.trim()});
         }

         return 1;
      })));
      builder.then(literal("list").executes(context -> {
         Map<String, Integer> saved = this.module().savedMap();
         if (saved.isEmpty()) {
            this.info("No saved times yet - use .timeset save <name>.", new Object[0]);
            return 1;
         } else {
            this.info("Saved times:", new Object[0]);

            for (Entry<String, Integer> e : saved.entrySet()) {
               this.info("  %s: %d (%s)", new Object[]{e.getKey(), e.getValue(), TimeSet.clock(e.getValue())});
            }

            return 1;
         }
      }));
      builder.then(literal("set").then(argument("time", StringArgumentType.greedyString()).executes(context -> {
         String text = StringArgumentType.getString(context, "time");
         if (!this.module().setFromText(text)) {
            this.info("Use a tick count (0-23999) or a preset: sunrise, morning, noon, afternoon, golden hour, sunset, dusk, midnight, server.", new Object[0]);
         } else {
            this.info("Time set to %s.", new Object[]{text.trim()});
            this.offNote();
         }

         return 1;
      })));
      builder.then(literal("reset").executes(context -> {
         this.module().reset();
         this.info("Showing the server's time.", new Object[0]);
         return 1;
      }));
   }

   private void offNote() {
      if (!this.module().isActive()) {
         this.info("Time Set is off - turn it on to see it.", new Object[0]);
      }
   }

   private TimeSet module() {
      return Modules.get().get(TimeSet.class);
   }
}
