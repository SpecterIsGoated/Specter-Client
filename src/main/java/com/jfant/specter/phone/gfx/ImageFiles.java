package com.jfant.specter.phone.gfx;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Reading pictures from disk (PNG or JPEG) and fitting them to a size, off the game thread. */
public final class ImageFiles {
   private ImageFiles() {
   }

   /**
    * Reads a picture by what it really is, not by its name: WebP (websites often save WebP pictures with a ".png"
    * name) through TwelveMonkeys' reader, everything else through Java's reader, then the stb_image decoder Minecraft
    * ships (PNG, JPEG, BMP, GIF, TGA...). AVIF/HEIC get a clear message instead.
    */
   public static BufferedImage read(Path file) throws IOException {
      byte[] bytes = java.nio.file.Files.readAllBytes(file);
      String kind = sniff(bytes);
      if ("WebP".equals(kind)) return webp(bytes);
      if (kind != null) throw new IOException("It's really a " + kind + " file - save it as PNG or JPG");
      BufferedImage img = null;
      try {
         img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
      } catch (IOException | RuntimeException ignored) {
         // fall back to stb below
      }
      if (img == null) img = stb(bytes);
      if (img == null) throw new IOException("Couldn't read that picture - try saving it as PNG");
      return img;
   }

   private static BufferedImage webp(byte[] bytes) throws IOException {
      javax.imageio.ImageReader reader = new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi().createReaderInstance(null);
      try (javax.imageio.stream.ImageInputStream in = new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
         reader.setInput(in);
         return reader.read(0);
      } catch (IOException | RuntimeException e) {
         throw new IOException("Couldn't read that WebP picture - try saving it as PNG", e);
      } finally {
         reader.dispose();
      }
   }

   /** Formats that need their own reader (WebP) or can't be opened at all (AVIF, HEIC): their name, or null. */
   private static String sniff(byte[] b) {
      if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "WebP";
      if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
         String brand = new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
         if (brand.startsWith("avi")) return "AVIF";
         if (brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("hev")) return "HEIC";
      }
      return null;
   }

   /** stb_image (bundled with Minecraft's LWJGL) as a second decoder. */
   private static BufferedImage stb(byte[] bytes) {
      java.nio.ByteBuffer data = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
      try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
         data.put(bytes).flip();
         java.nio.IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), ch = stack.mallocInt(1);
         java.nio.ByteBuffer pixels = org.lwjgl.stb.STBImage.stbi_load_from_memory(data, w, h, ch, 4);
         if (pixels == null) return null;
         try {
            int width = w.get(0), height = h.get(0);
            if (width <= 0 || height <= 0 || (long)width * height > 40_000_000L) return null;
            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
               for (int x = 0; x < width; x++) {
                  int i = (y * width + x) * 4;
                  row[x] = (pixels.get(i) & 255) << 16 | (pixels.get(i + 1) & 255) << 8 | pixels.get(i + 2) & 255;
               }
               out.setRGB(0, y, width, 1, row, 0, width);
            }
            return out;
         } finally {
            org.lwjgl.stb.STBImage.stbi_image_free(pixels);
         }
      } catch (Throwable t) {
         return null;
      } finally {
         org.lwjgl.system.MemoryUtil.memFree(data);
      }
   }

   /** Scales to fill w x h exactly, cropping the overflow from the middle (like a phone wallpaper). */
   public static BufferedImage cover(BufferedImage img, int w, int h) {
      double k = Math.max((double)w / img.getWidth(), (double)h / img.getHeight());
      int sw = (int)Math.ceil(img.getWidth() * k), sh = (int)Math.ceil(img.getHeight() * k);
      BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(img, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
      g.dispose();
      return out;
   }

   public static NativeImage toNative(BufferedImage img) {
      int w = img.getWidth(), h = img.getHeight();
      int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
      NativeImage out = new NativeImage(w, h, false);
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) out.setPixel(x, y, 0xFF000000 | argb[y * w + x]);
      }
      return out;
   }

   /** Average colour (for the 3D screen while the picture loads). */
   public static int average(BufferedImage img) {
      long r = 0, g = 0, b = 0, n = 0;
      for (int y = 0; y < img.getHeight(); y += 8) {
         for (int x = 0; x < img.getWidth(); x += 8) {
            int p = img.getRGB(x, y);
            r += p >> 16 & 255;
            g += p >> 8 & 255;
            b += p & 255;
            n++;
         }
      }
      return n == 0 ? 0xFF1A1526 : Colors.argb(255, (int)(r / n), (int)(g / n), (int)(b / n));
   }
}
