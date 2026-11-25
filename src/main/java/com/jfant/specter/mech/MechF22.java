package com.jfant.specter.mech;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.joml.Vector3f;

/**
 * The real F-22 airframe the Jet Mech turns into: "Lockheed Martin F-22 Raptor" by andertan (CC BY 4.0, see
 * assets/specter/models/mech/CREDITS.txt), cut into the mech's moving parts by tools/mech/f22build.py.
 *
 * <p>Everything is in jet space (+X left wing, +Y up, +Z nose, blocks), already scaled to the mech. Each segment is a
 * triangle list with a material id per triangle and a smooth normal per corner. Hinge lines and pivots come along for
 * the parts that move in flight.
 */
public final class MechF22 {
   public static final String RESOURCE = "/assets/specter/models/mech/f22.bin";

   /** One cut-out piece of the airframe. */
   public static final class Segment {
      public final String name;
      public final int tris;
      /** tris x 3 corners x (x, y, z). */
      public final float[] pos;
      /** tris x 3 corners x (nx, ny, nz). */
      public final float[] normal;
      public final byte[] material;

      Segment(String name, int tris) {
         this.name = name;
         this.tris = tris;
         this.pos = new float[tris * 9];
         this.normal = new float[tris * 9];
         this.material = new byte[tris];
      }
   }

   public final Map<String, Segment> segments = new HashMap<>();
   /** Hinge line / pivot per part name: two points (A, B). Nozzles: A = hinge at the front, B = exit centre. */
   public final Map<String, Vector3f[]> hinges = new HashMap<>();

   private static MechF22 instance;
   private static boolean failed;

   /** The airframe, or null if the resource is missing or broken (the mech then keeps its own blocky jet). */
   public static synchronized MechF22 get() {
      if (instance == null && !failed) {
         try (InputStream raw = MechF22.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IOException("missing " + RESOURCE);
            instance = read(new DataInputStream(new BufferedInputStream(raw)));
         } catch (IOException | RuntimeException e) {
            failed = true;
            System.err.println("[Specter] Jet Mech: could not load the F-22 model: " + e);
         }
      }
      return instance;
   }

   static MechF22 read(DataInputStream in) throws IOException {
      byte[] magic = new byte[4];
      in.readFully(magic);
      if (!"F22M".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IOException("not an F22M file");
      MechF22 f = new MechF22();
      int count = in.readInt();
      for (int s = 0; s < count; s++) {
         String name = readName(in);
         int tris = in.readInt();
         if (tris < 0 || tris > 200_000) throw new IOException("bad triangle count " + tris);
         Segment seg = new Segment(name, tris);
         for (int t = 0; t < tris; t++) {
            seg.material[t] = in.readByte();
            for (int c = 0; c < 3; c++) {
               int o = t * 9 + c * 3;
               seg.pos[o] = in.readFloat();
               seg.pos[o + 1] = in.readFloat();
               seg.pos[o + 2] = in.readFloat();
               seg.normal[o] = in.readFloat();
               seg.normal[o + 1] = in.readFloat();
               seg.normal[o + 2] = in.readFloat();
            }
         }
         f.segments.put(name, seg);
      }
      int hinges = in.readInt();
      for (int h = 0; h < hinges; h++) {
         String name = readName(in);
         Vector3f a = new Vector3f(in.readFloat(), in.readFloat(), in.readFloat());
         Vector3f b = new Vector3f(in.readFloat(), in.readFloat(), in.readFloat());
         f.hinges.put(name, new Vector3f[]{a, b});
      }
      return f;
   }

   private static String readName(DataInputStream in) throws IOException {
      int len = in.readUnsignedShort();
      byte[] b = new byte[len];
      in.readFully(b);
      return new String(b, StandardCharsets.UTF_8);
   }
}
