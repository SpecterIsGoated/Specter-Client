package com.jfant.specter.phone.gfx;

import com.jfant.specter.SpecterClientAddon;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Every texture the phone makes: painted icons and wallpapers (cached per pixel size, so they are always drawn 1:1
 * and stay sharp) and decoded network images. Big paintings run on a background thread and are uploaded on the
 * render thread; until then {@link #get} answers null and callers draw a plain colour. Old entries are released
 * a second after they leave the cache, so a texture still queued in the current frame is never freed under it.
 */
public final class PhoneTextures {
   private static final int MAX_PAINTED = 72;
   private static final long RETIRE_NANOS = 1_000_000_000L;
   /** One daemon thread: paintings are cheap enough to queue, and never compete with chunk building. */
   private static final ExecutorService PAINTER = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Specter-Phone-Painter");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      return t;
   });
   private static final Map<String, Identifier> painted = new LinkedHashMap<>(32, 0.75F, true);
   private static final Map<String, CompletableFuture<NativeImage>> pending = new HashMap<>();
   private static final ConcurrentLinkedQueue<Runnable> uploads = new ConcurrentLinkedQueue<>();
   private static final ArrayDeque<Retired> retired = new ArrayDeque<>();
   private static final Map<String, Identifier> images = new HashMap<>();
   private static int next;

   private PhoneTextures() {
   }

   /** A linearly filtered, edge-clamped texture: scaled photos and wallpapers stay smooth. */
   private static final class Linear extends DynamicTexture {
      Linear(String label, NativeImage image) {
         super(() -> label, image);
         this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
      }
   }

   /** Painted right now (small things: icons). */
   public static Identifier paint(String key, int width, int height, Consumer<IconRaster> painter) {
      String full = key + "@" + width + "x" + height;
      Identifier id = painted.get(full);
      if (id != null) return id;
      IconRaster raster = new IconRaster(width, height);
      try {
         painter.accept(raster);
      } catch (RuntimeException e) {
         SpecterClientAddon.LOG.warn("Donut Phone: could not paint {}", key, e);
      }
      id = register("phone/" + sanitize(key), raster.toImage());
      painted.put(full, id);
      trim();
      return id;
   }

   /** Painted on the background thread (wallpapers): null until it is ready, then the texture. */
   @Nullable
   public static Identifier paintLater(String key, int width, int height, Consumer<IconRaster> painter) {
      String full = key + "@" + width + "x" + height;
      Identifier id = painted.get(full);
      if (id != null) return id;
      if (!pending.containsKey(full)) {
         CompletableFuture<NativeImage> job = CompletableFuture.supplyAsync(() -> {
            IconRaster raster = new IconRaster(width, height);
            painter.accept(raster);
            return raster.toImage();
         }, PAINTER);
         pending.put(full, job);
         job.whenComplete((image, error) -> uploads.add(() -> {
            pending.remove(full);
            if (error != null || image == null) {
               SpecterClientAddon.LOG.warn("Donut Phone: could not paint {}", key, error);
               return;
            }
            painted.put(full, register("phone/" + sanitize(key), image));
            trim();
         }));
      }
      return null;
   }

   /** Most recent finished painting for {@code key} at any size (a stand-in while a new size paints). */
   @Nullable
   public static Identifier anySize(String key) {
      String prefix = key + "@";
      Identifier best = null;
      for (Map.Entry<String, Identifier> e : painted.entrySet()) {
         if (e.getKey().startsWith(prefix)) best = e.getValue();
      }
      return best;
   }

   /** Uploads a decoded image under {@code key}, replacing (and later freeing) an older one with the same key. */
   public static Identifier image(String key, NativeImage image) {
      Identifier old = images.remove(key);
      if (old != null) retire(old);
      Identifier id = register("phone/img/" + sanitize(key), image);
      images.put(key, id);
      return id;
   }

   public static void releaseImage(String key) {
      Identifier old = images.remove(key);
      if (old != null) retire(old);
   }

   /** Drops every painted texture (theme or scale changed). They repaint on next use. */
   public static void clearPainted() {
      for (Identifier id : painted.values()) retire(id);
      painted.clear();
   }

   /** Render thread, once per frame: finished background paintings are uploaded, retired textures freed. */
   public static void frame() {
      Runnable job;
      int budget = 2;
      while (budget-- > 0 && (job = uploads.poll()) != null) job.run();
      long now = System.nanoTime();
      while (!retired.isEmpty() && now - retired.peek().at > RETIRE_NANOS) {
         Minecraft.getInstance().getTextureManager().release(retired.poll().id);
      }
   }

   private static Identifier register(String label, NativeImage image) {
      Identifier id = Identifier.fromNamespaceAndPath("specter-client", label + "_" + next++);
      Minecraft.getInstance().getTextureManager().register(id, new Linear("specter-" + label, image));
      return id;
   }

   private static void trim() {
      Iterator<Map.Entry<String, Identifier>> it = painted.entrySet().iterator();
      while (painted.size() > MAX_PAINTED && it.hasNext()) {
         retire(it.next().getValue());
         it.remove();
      }
   }

   private static void retire(Identifier id) {
      retired.add(new Retired(id, System.nanoTime()));
   }

   private static String sanitize(String key) {
      StringBuilder sb = new StringBuilder(key.length());
      for (char c : key.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
         sb.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '/' || c == '.' || c == '-' ? c : '_');
      }
      return sb.toString();
   }

   private record Retired(Identifier id, long at) {
   }
}
