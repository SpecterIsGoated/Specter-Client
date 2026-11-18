package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.bypass.BypassToast;
import com.jfant.specter.cave.CaveAnalysis;
import com.jfant.specter.cave.CaveMemory;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class DrillTunnelFinderEsp extends Module {
   private static final int NOTIFY_REACH = 2;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAlerts = this.settings.createGroup("Alerts");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> range = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("range")
            .description("How many chunks around you to look at, counting chunks remembered from earlier this session.")
            .defaultValue(16)
            .range(4, 32)
            .sliderRange(4, 32)
            .build()
      );
   private final Setting<Integer> minY = this.sgGeneral
      .add(new IntSetting.Builder().name("min-y").description("The lowest height looked at.").defaultValue(-64).range(-64, 320).sliderRange(-64, 128).build());
   private final Setting<Integer> maxY = this.sgGeneral
      .add(new IntSetting.Builder().name("max-y").description("The highest height looked at.").defaultValue(128).range(-64, 320).sliderRange(-64, 320).build());
   private final Setting<Integer> minTunnel = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-tunnel")
            .description("How long a straight tunnel has to be, in blocks.")
            .defaultValue(8)
            .range(4, 64)
            .sliderRange(4, 32)
            .build()
      );
   private final Setting<Integer> maxTunnels = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-tunnels")
            .description("How many of the nearest tunnel spots to mark.")
            .defaultValue(8)
            .range(0, 30)
            .sliderRange(0, 15)
            .build()
      );
   private final Setting<Boolean> skipOwn = this.sgGeneral
      .add(new BoolSetting.Builder().name("skip-own").description("Leave out tunnels you dug yourself this session.").defaultValue(true).build());
   private final Setting<Boolean> chat = this.sgAlerts
      .add(new BoolSetting.Builder().name("chat").description("Say in chat when a new tunnel is found.").defaultValue(true).build());
   private final Setting<Boolean> popup = this.sgAlerts
      .add(new BoolSetting.Builder().name("popup").description("Show a pop-up when a new tunnel is found.").defaultValue(true).build());
   private final Setting<Boolean> sound = this.sgAlerts
      .add(new BoolSetting.Builder().name("sound").description("Play a bell with the pop-up.").defaultValue(true).visible(this.popup::get).build());
   private final Setting<SettingColor> color = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("color")
            .description("The colour of tunnel boxes and labels.")
            .defaultValue(new SettingColor(255, 150, 30, 70))
            .build()
      );
   private final Setting<Boolean> beams = this.sgRender
      .add(new BoolSetting.Builder().name("beams").description("Draw a beam up from each tunnel.").defaultValue(true).build());
   private final Setting<SettingColor> beamColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("beam-color")
            .description("The colour of the beams.")
            .defaultValue(new SettingColor(170, 60, 255, 255))
            .visible(this.beams::get)
            .build()
      );
   private final Setting<Boolean> labels = this.sgRender
      .add(new BoolSetting.Builder().name("labels").description("Label each tunnel with what it is and how long.").defaultValue(true).build());
   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw a line from you to each tunnel.").defaultValue(false).build());
   private final CaveMemory memory = new CaveMemory("Drill Tunnel Finder Esp");
   private List<CaveAnalysis.Spot> spots = List.of();
   private CaveAnalysis.Result result;
   private final LongOpenHashSet announced = new LongOpenHashSet();
   private final Vector3d labelPos = new Vector3d();
   private final Color line = new Color();
   private final Color beamLine = new Color();
   private static final Color LABEL_TEXT = new Color(235, 235, 235);

   public DrillTunnelFinderEsp() {
      super(SpecterClientAddon.ESP, "drill-tunnel-finder-esp", "Finds straight hand-dug tunnels, marked with a purple beam.");
   }

   @Override
   public void onActivate() {
      this.memory.start();
      this.spots = List.of();
      this.result = null;
      this.announced.clear();
   }

   @Override
   public void onDeactivate() {
      SpecterClientAddon.LOG.info("Drill Tunnel Finder Esp: off - {} chunks remembered, {} tunnel spots", this.memory.remembered(), this.spots.size());
      this.memory.stop();
      this.spots = List.of();
      this.result = null;
      this.announced.clear();
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      if (this.mc.player != null) {
         this.memory.onChunkData(event.chunk(), this.mc.player.getX(), this.mc.player.getZ(), this.range.get());
      }
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      this.memory.onBlockUpdate(event.pos);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      CaveAnalysis.Settings s = new CaveAnalysis.Settings(2147483647, false, true, this.minTunnel.get(), this.skipOwn.get());
      CaveAnalysis.Result r = this.memory.tick(this.mc, this.range.get(), this.minY.get(), this.maxY.get(), s);
      if (r != null && this.mc.player != null) {
         this.result = r;
         List<CaveAnalysis.Spot> sorted = new ArrayList<>(r.spots());
         double px = this.mc.player.getX();
         double pz = this.mc.player.getZ();
         sorted.sort(Comparator.comparingDouble(sp -> (sp.x() + 0.5 - px) * (sp.x() + 0.5 - px) + (sp.z() + 0.5 - pz) * (sp.z() + 0.5 - pz)));
         this.spots = sorted;

         for (CaveAnalysis.Spot spot : this.spots) {
            this.announce(spot);
         }
      }
   }

   private void announce(CaveAnalysis.Spot spot) {
      int cx = spot.x() >> 4;
      int cz = spot.z() >> 4;

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (this.announced.contains(ChunkPos.pack(cx + dx, cz + dz))) {
               return;
            }
         }
      }

      this.announced.add(ChunkPos.pack(cx, cz));
      String text = title(spot) + " at " + spot.x() + ", " + spot.y() + ", " + spot.z() + " (" + spot.length() + " blocks long)";
      SpecterClientAddon.LOG.info("Drill Tunnel Finder Esp: {} ({}x{})", new Object[]{text, spot.w(), spot.h()});
      if (this.chat.get()) {
         this.info("%s", new Object[]{text});
      }

      if (this.popup.get()) {
         BypassToast.addOrUpdate(new ItemStack(Items.IRON_PICKAXE), title(spot), Component.literal(text), this.sound.get());
      }
   }

   private static String title(CaveAnalysis.Spot spot) {
      return spot.count() > 1 ? "Drill Tunnels x" + spot.count() : "Drill Tunnel";
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      int shown = Math.min(this.maxTunnels.get(), this.spots.size());
      if (shown != 0) {
         SettingColor c = this.color.get();
         SettingColor b = this.beamColor.get();
         this.line.set(c.r, c.g, c.b, 255);
         this.beamLine.set(b.r, b.g, b.b, b.a);
         Vec3 from = RenderUtils.center;

         for (int i = 0; i < shown; i++) {
            CaveAnalysis.Spot spot = this.spots.get(i);
            event.renderer.box(spot.x(), spot.y(), spot.z(), spot.x() + 1, spot.y() + 1, spot.z() + 1, c, this.line, ShapeMode.Both, 0);
            if (this.beams.get()) {
               event.renderer.line(spot.x() + 0.5, spot.y() + 1, spot.z() + 0.5, spot.x() + 0.5, spot.y() + 48, spot.z() + 0.5, this.beamLine);
            }

            if (this.tracers.get() && from != null) {
               event.renderer.line(from.x, from.y, from.z, spot.x() + 0.5, spot.y() + 0.5, spot.z() + 0.5, this.line);
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.labels.get()) {
         int shown = Math.min(this.maxTunnels.get(), this.spots.size());
         if (shown != 0) {
            TextRenderer text = TextRenderer.get();
            SettingColor c = this.color.get();
            this.line.set(c.r, c.g, c.b, 255);

            for (int i = 0; i < shown; i++) {
               CaveAnalysis.Spot spot = this.spots.get(i);
               this.labelPos.set(spot.x() + 0.5, spot.y() + 1.8, spot.z() + 0.5);
               if (NametagUtils.to2D(this.labelPos, 1.3)) {
                  String head = title(spot);
                  String second = spot.length() + " blocks";
                  NametagUtils.begin(this.labelPos);
                  text.begin();
                  double h = text.getHeight(true);
                  text.render(head, -text.getWidth(head, true) / 2.0, -h * 2.0, this.line, true);
                  text.render(second, -text.getWidth(second, true) / 2.0, -h, LABEL_TEXT, true);
                  text.end();
                  NametagUtils.end();
               }
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      return this.result == null ? null : String.valueOf(this.spots.size());
   }
}
