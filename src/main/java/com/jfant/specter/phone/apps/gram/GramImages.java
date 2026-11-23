package com.jfant.specter.phone.apps.gram;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.phone.core.PhoneConfig;
import com.jfant.specter.phone.gfx.PhoneTextures;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Images for DonutGram: downloaded post pictures (memory cache of textures, plus a disk cache that forgets anything
 * older than a day since posts only live that long), camera photos to post (scaled to 1280 x 720 at most and encoded as
 * JPEG off the game thread). Posting a file from disk is deliberately impossible.
 */
public final class GramImages {
   public static final int MAX_W = 1280;
   public static final int MAX_H = 720;
   private static final int MEMORY = 36;
   private static final ExecutorService WORK = Executors.newFixedThreadPool(2, r -> {
      Thread t = new Thread(r, "Specter-DonutGram-Images");
      t.setDaemon(true);
      return t;
   });
   private static final Path CACHE = PhoneConfig.DIR.resolve("apps").resolve("donutgram").resolve("cache");

   public record Loaded(Identifier texture, int width, int height) {
   }

   private static final Map<String, Loaded> loaded = new LinkedHashMap<>(32, 0.75F, true);
   private static final Map<String, Boolean> loading = new HashMap<>();
   private static boolean pruned;

   private GramImages() {
   }

   /** The texture for a post's picture, or null while it downloads (call every frame; it starts the download once). */
   @Nullable
   public static Loaded post(String id) {
      Loaded l = loaded.get(id);
      if (l != null) return l;
      if (loading.containsKey(id)) return null;
      loading.put(id, Boolean.TRUE);
      prune();
      Path file = CACHE.resolve(id + ".img");
      CompletableFuture<byte[]> bytes = Files.isRegularFile(file)
         ? CompletableFuture.supplyAsync(() -> {
            try {
               return Files.readAllBytes(file);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }, WORK)
         : GramService.get().image(id).thenApplyAsync(b -> {
            try {
               Files.createDirectories(CACHE);
               Files.write(file, b);
            } catch (IOException ignored) {
            }
            return b;
         }, WORK);
      bytes.thenApplyAsync(GramImages::decode, WORK).whenComplete((img, err) -> Minecraft.getInstance().execute(() -> {
         if (err != null || img == null) {
            loading.put(id, Boolean.FALSE);
            return;
         }
         loading.remove(id);
         int w = img.getWidth(), h = img.getHeight();
         loaded.put(id, new Loaded(PhoneTextures.image("gram/" + id, img), w, h));
         trim();
      }));
      return null;
   }

   /**
    * Decodes a post picture. Posts are JPEG, which Minecraft's own image loader can't read (it only takes PNG), so
    * this goes through ImageIO and copies the pixels into a NativeImage.
    */
   static NativeImage decode(byte[] bytes) {
      try {
         BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
         if (img == null) throw new IOException("not an image");
         int w = img.getWidth(), h = img.getHeight();
         int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
         NativeImage out = new NativeImage(w, h, false);
         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out.setPixel(x, y, 0xFF000000 | argb[y * w + x]);
         }
         return out;
      } catch (IOException e) {
         SpecterClientAddon.LOG.warn("DonutGram: could not decode a picture", e);
         throw new RuntimeException(e);
      }
   }

   /** Did the download fail (so the card can say so instead of spinning)? */
   public static boolean failed(String id) {
      return Boolean.FALSE.equals(loading.get(id));
   }

   public static void retry(String id) {
      if (failed(id)) loading.remove(id);
   }

   private static void trim() {
      Iterator<Map.Entry<String, Loaded>> it = loaded.entrySet().iterator();
      while (loaded.size() > MEMORY && it.hasNext()) {
         Map.Entry<String, Loaded> e = it.next();
         PhoneTextures.releaseImage("gram/" + e.getKey());
         it.remove();
      }
   }

   /** Frees every post texture (app closed). */
   public static void releaseAll() {
      for (String id : loaded.keySet()) PhoneTextures.releaseImage("gram/" + id);
      loaded.clear();
      loading.clear();
   }

   private static void prune() {
      if (pruned) return;
      pruned = true;
      WORK.execute(() -> {
         File[] files = CACHE.toFile().listFiles();
         if (files == null) return;
         long cutoff = System.currentTimeMillis() - 26L * 3600 * 1000;
         for (File f : files) if (f.lastModified() < cutoff) f.delete();
      });
   }

   // ============================================================================================== posting

   /** A picture ready to post: the encoded bytes and a small preview texture. */
   public record Prepared(byte[] bytes, String mime, int width, int height, Identifier preview) {
   }

   /** Scales and encodes a captured frame (the NativeImage is closed here). Completes on the game thread. */
   public static CompletableFuture<Prepared> fromCapture(NativeImage image) {
      int w = image.getWidth(), h = image.getHeight();
      int[] abgr = image.getPixelsABGR();
      image.close();
      BufferedImage src = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      CompletableFuture<BufferedImage> f = CompletableFuture.supplyAsync(() -> {
         int[] rgb = new int[abgr.length];
         for (int i = 0; i < abgr.length; i++) {
            int p = abgr[i];
            rgb[i] = (p & 0xFF) << 16 | (p >> 8 & 0xFF) << 8 | (p >> 16 & 0xFF);
         }
         src.setRGB(0, 0, w, h, rgb, 0, w);
         return src;
      }, WORK);
      return prepare(f);
   }

   private static CompletableFuture<Prepared> prepare(CompletableFuture<BufferedImage> source) {
      CompletableFuture<Object[]> work = source.thenApplyAsync(img -> {
         BufferedImage scaled = fit(img, MAX_W, MAX_H);
         byte[] bytes;
         String mime = "image/jpeg";
         try {
            bytes = jpeg(scaled, 0.86F);
            if (bytes.length > 950_000) bytes = jpeg(scaled, 0.7F);
         } catch (IOException | RuntimeException e) {
            SpecterClientAddon.LOG.warn("DonutGram: JPEG encoding failed, using PNG", e);
            try {
               ByteArrayOutputStream out = new ByteArrayOutputStream();
               ImageIO.write(fit(scaled, 960, 540), "png", out);
               bytes = out.toByteArray();
               mime = "image/png";
            } catch (IOException e2) {
               throw new RuntimeException("Couldn't encode the picture.", e2);
            }
         }
         BufferedImage thumb = fit(scaled, 480, 480);
         return new Object[]{bytes, mime, scaled.getWidth(), scaled.getHeight(), toNative(thumb)};
      }, WORK);
      CompletableFuture<Prepared> out = new CompletableFuture<>();
      work.whenComplete((r, e) -> Minecraft.getInstance().execute(() -> {
         if (e != null) {
            out.completeExceptionally(e);
            return;
         }
         Identifier preview = PhoneTextures.image("gram/compose", (NativeImage)r[4]);
         out.complete(new Prepared((byte[])r[0], (String)r[1], (int)r[2], (int)r[3], preview));
      }));
      return out;
   }

   static BufferedImage fit(BufferedImage img, int maxW, int maxH) {
      double k = Math.min(1.0, Math.min((double)maxW / img.getWidth(), (double)maxH / img.getHeight()));
      int w = Math.max(1, (int)Math.round(img.getWidth() * k)), h = Math.max(1, (int)Math.round(img.getHeight() * k));
      if (k >= 1.0 && img.getType() == BufferedImage.TYPE_INT_RGB) return img;
      BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(img, 0, 0, w, h, null);
      g.dispose();
      return out;
   }

   private static byte[] jpeg(BufferedImage img, float quality) throws IOException {
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
         writer.setOutput(stream);
         ImageWriteParam p = writer.getDefaultWriteParam();
         p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         p.setCompressionQuality(quality);
         writer.write(null, new IIOImage(img, null, null), p);
      } finally {
         writer.dispose();
      }
      return out.toByteArray();
   }

   private static NativeImage toNative(BufferedImage img) {
      NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
      for (int y = 0; y < img.getHeight(); y++) {
         for (int x = 0; x < img.getWidth(); x++) out.setPixel(x, y, 0xFF000000 | img.getRGB(x, y));
      }
      return out;
   }
}
