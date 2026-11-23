package com.jfant.specter.phone.core;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.phone.gfx.ImageFiles;
import com.jfant.specter.phone.gfx.PhoneTextures;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Your own wallpapers: pictures in {@code specter/phone/wallpapers} (added with the file picker in Settings, or by
 * dropping a picture on the game window while the phone is out). A 9:16 picture fits best; anything else is fitted
 * by cropping the middle. Wallpaper ids are {@code custom:<file name>}.
 */
public final class CustomWallpapers {
   public static final Path DIR = PhoneConfig.DIR.resolve("wallpapers");
   public static final String PREFIX = "custom:";
   /** Wallpaper size: 3 pixels per phone point (180 x 392 points). */
   private static final int W = 540;
   private static final int H = 1176;
   private static final long MAX_BYTES = 25L * 1024 * 1024;

   private static String loadedId;
   private static Identifier texture;
   private static String loadingId;
   private static int swatch = 0xFF1A1526;
   private static final Map<String, Identifier> thumbs = new HashMap<>();
   private static final Set<String> thumbsLoading = new HashSet<>();
   private static List<String> listed;

   private CustomWallpapers() {
   }

   public static boolean isCustom(String id) {
      return id != null && id.startsWith(PREFIX);
   }

   private static Path file(String id) {
      return DIR.resolve(id.substring(PREFIX.length()));
   }

   /** The picture as a texture, or null while it loads (or if the file is gone). */
   @Nullable
   public static Identifier texture(String id) {
      if (id.equals(loadedId)) return texture;
      if (!id.equals(loadingId)) {
         loadingId = id;
         Path path = file(id);
         CompletableFuture.supplyAsync(() -> {
            try {
               BufferedImage fitted = ImageFiles.cover(ImageFiles.read(path), W, H);
               return new Object[]{ImageFiles.toNative(fitted), ImageFiles.average(fitted)};
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }).whenComplete((r, err) -> Minecraft.getInstance().execute(() -> {
            if (!id.equals(loadingId)) return;
            if (err != null) {
               SpecterClientAddon.LOG.warn("Donut Phone: could not load wallpaper {}", path, err);
               return;
            }
            texture = PhoneTextures.image("wallpaper/custom", (com.mojang.blaze3d.platform.NativeImage)r[0]);
            swatch = (int)r[1];
            loadedId = id;
         }));
      }
      return null;
   }

   public static int swatch() {
      return swatch;
   }

   /** Your pictures, newest first (read from the folder once, again after adding or removing). */
   public static List<String> list() {
      if (listed == null) {
         List<String> out = new ArrayList<>();
         File[] files = DIR.toFile().listFiles((d, n) -> isPicture(n));
         if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (File f : files) out.add(PREFIX + f.getName());
         }
         listed = out;
      }
      return listed;
   }

   /** A small preview, or null while it loads. */
   @Nullable
   public static Identifier thumb(String id) {
      Identifier t = thumbs.get(id);
      if (t != null || !thumbsLoading.add(id)) return t;
      Path path = file(id);
      CompletableFuture.supplyAsync(() -> {
         try {
            return ImageFiles.toNative(ImageFiles.cover(ImageFiles.read(path), 120, 261));
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }).whenComplete((img, err) -> Minecraft.getInstance().execute(() -> {
         if (err == null) thumbs.put(id, PhoneTextures.image("wallpaper/thumb/" + id.substring(PREFIX.length()), img));
      }));
      return null;
   }

   public static boolean isPicture(String name) {
      String n = name.toLowerCase(Locale.ROOT);
      return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
   }

   /** Copies a picture into the wallpapers folder; returns its wallpaper id. */
   public static String add(Path source) throws IOException {
      if (!isPicture(source.getFileName().toString())) throw new IOException("Pick a PNG, JPG or WebP picture.");
      if (Files.size(source) > MAX_BYTES) throw new IOException("That picture is too big (25 MB max).");
      ImageFiles.read(source); // make sure it really is a picture we can read
      Files.createDirectories(DIR);
      String name = source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
      Path target = DIR.resolve(name);
      int n = 2;
      while (Files.exists(target) && !Files.isSameFile(target, source)) {
         int dot = name.lastIndexOf('.');
         target = DIR.resolve(name.substring(0, dot) + "_" + n++ + name.substring(dot));
      }
      if (!Files.exists(target)) Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
      target.toFile().setLastModified(System.currentTimeMillis());
      listed = null;
      return PREFIX + target.getFileName();
   }

   public static void remove(String id) {
      try {
         Files.deleteIfExists(file(id));
      } catch (IOException e) {
         SpecterClientAddon.LOG.warn("Donut Phone: could not delete wallpaper {}", id, e);
      }
      Identifier t = thumbs.remove(id);
      if (t != null) PhoneTextures.releaseImage("wallpaper/thumb/" + id.substring(PREFIX.length()));
      thumbsLoading.remove(id);
      if (id.equals(loadedId)) {
         loadedId = null;
         loadingId = null;
      }
      listed = null;
   }
}
