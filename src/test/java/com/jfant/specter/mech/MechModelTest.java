package com.jfant.specter.mech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class MechModelTest {
   @Test
   void everyPartHasGeometryAndUnitNormals() {
      MechModel model = MechModel.get();
      for (MechModel.Part p : model.parts) {
         assertTrue(p.mesh.quads > 0, p.name + " has no quads");
         for (int q = 0; q < p.mesh.quads; q++) {
            float nx = p.mesh.normal[q * 3], ny = p.mesh.normal[q * 3 + 1], nz = p.mesh.normal[q * 3 + 2];
            assertEquals(1.0, Math.sqrt(nx * nx + ny * ny + nz * nz), 1.0E-3, p.name + " normal");
         }
      }
      assertEquals(MechModel.COUNT, model.order().length);
   }

   @Test
   void posesAreFiniteAcrossTheWholeTransformation() {
      MechPose pose = new MechPose();
      MechInputs in = new MechInputs();
      for (int i = 0; i <= 40; i++) {
         in.transform = i / 40.0F;
         in.walkSpeed = 0.6F;
         in.walkPos = i * 0.7F;
         in.heading = 30;
         in.pitch = -20;
         in.roll = 25;
         in.time = i * 0.1F;
         in.gear = i < 20 ? 1 : 0;
         pose.compute(in);
         for (int p = 0; p < MechModel.COUNT; p++) {
            assertFalse(Float.isNaN(pose.pos[p].x + pose.pos[p].y + pose.pos[p].z), "pos " + p);
            assertFalse(Float.isNaN(pose.rot[p].x + pose.rot[p].w), "rot " + p);
         }
      }
   }

   @Test
   void robotStandsOnItsFeetAndJetSitsOnItsCentre() {
      MechPose pose = new MechPose();
      MechInputs in = new MechInputs();
      in.transform = 0;
      pose.compute(in);
      float[] box = bounds(pose);
      assertEquals(0.0, box[1], 0.04, "robot feet on the ground");
      assertTrue(box[4] > 1.9 && box[4] < 2.45, "robot height " + box[4]);
      in.transform = 1;
      in.jetY = 0.32F;
      in.gear = 0;
      pose.compute(in);
      box = bounds(pose);
      float length = box[5] - box[2];
      float span = box[3] - box[0];
      assertTrue(length > 2.6 && length < 3.6, "jet length " + length);
      assertTrue(span > 1.7 && span < 2.6, "jet span " + span);
   }

   /** min x, y, z, max x, y, z over all vertices. */
   static float[] bounds(MechPose pose) {
      float[] b = {1e9F, 1e9F, 1e9F, -1e9F, -1e9F, -1e9F};
      Vector3f v = new Vector3f();
      MechModel model = MechModel.get();
      for (int p = 0; p < MechModel.COUNT; p++) {
         for (int layer = 0; layer < 2; layer++) {
         MechMesh m = layer == 0 ? model.parts[p].mesh : model.parts[p].shell;
         float drawScale = pose.scale[p] * (layer == 0 ? pose.coreScale[p] : pose.shellScale[p]);
         if (m == null || pose.scale[p] < 0.5F || drawScale < 0.5F) continue; // stowed bits
         for (int k = 0; k < m.quads * 4; k++) {
            v.set(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2]).mul(drawScale);
            pose.rot[p].transform(v).add(pose.pos[p]);
            b[0] = Math.min(b[0], v.x);
            b[1] = Math.min(b[1], v.y);
            b[2] = Math.min(b[2], v.z);
            b[3] = Math.max(b[3], v.x);
            b[4] = Math.max(b[4], v.y);
            b[5] = Math.max(b[5], v.z);
         }
         }
      }
      return b;
   }

   /** Dev aid: -Dmech.obj=<dir> writes OBJ snapshots of the transformation for a quick look in Blender. */
   @Test
   void exportObjSnapshots() throws IOException {
      String dir = System.getProperty("mech.obj");
      if (dir == null || dir.isEmpty()) return;
      Path out = Path.of(dir);
      Files.createDirectories(out);
      writeMtl(out.resolve("mech.mtl"));
      MechInputs in = new MechInputs();
      float[] steps = {0, 0.12F, 0.25F, 0.4F, 0.55F, 0.7F, 0.85F, 1};
      for (float t : steps) {
         in.transform = t;
         in.gear = 0;
         in.jetY = 0.9F;
         export(out.resolve(String.format("t%03d.obj", Math.round(t * 100))), in);
      }
      MechInputs walk = new MechInputs();
      walk.walkSpeed = 1;
      walk.walkPos = 1.3F;
      walk.sprint = 1;
      export(out.resolve("run.obj"), walk);
      MechInputs bank = new MechInputs();
      bank.transform = 1;
      bank.roll = 35;
      bank.pitch = -12;
      bank.aileron = 0.8F;
      bank.jetY = 0.9F;
      export(out.resolve("bank.obj"), bank);
      MechInputs ground = new MechInputs();
      ground.transform = 1;
      ground.gear = 1;
      ground.jetY = 0.47F;
      export(out.resolve("ground.obj"), ground);
   }

   private static void export(Path file, MechInputs in) throws IOException {
      MechPose pose = new MechPose();
      pose.compute(in);
      MechModel model = MechModel.get();
      try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
         w.println("mtllib mech.mtl");
         int base = 1;
         Vector3f v = new Vector3f();
         for (int layer = 0; layer < 2; layer++)
         for (int p = 0; p < MechModel.COUNT; p++) {
            MechMesh m = layer == 0 ? model.parts[p].mesh : model.parts[p].shell;
            if (m == null) continue;
            float drawScale = pose.scale[p] * (layer == 0 ? pose.coreScale[p] : pose.shellScale[p]);
            if (drawScale < 0.02F) continue;
            Quaternionf r = pose.rot[p];
            w.println("o " + model.parts[p].name + (layer == 0 ? "" : "-skin"));
            for (int k = 0; k < m.quads * 4; k++) {
               v.set(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2]).mul(drawScale);
               r.transform(v).add(pose.pos[p]);
               w.printf("v %.5f %.5f %.5f%n", v.x, v.y, v.z);
            }
            int last = -1;
            for (int q = 0; q < m.quads; q++) {
               if (m.material[q] != last) {
                  last = m.material[q];
                  w.println("usemtl m" + last);
               }
               int a = base + q * 4;
               w.printf("f %d %d %d %d%n", a, a + 1, a + 2, a + 3);
            }
            base += m.quads * 4;
         }
      }
   }

   private static void writeMtl(Path file) throws IOException {
      MechPalette pal = new MechPalette();
      try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
         for (int i = 0; i < MechPalette.COUNT; i++) {
            int c = pal.rgb[i];
            w.println("newmtl m" + i);
            w.printf("Kd %.4f %.4f %.4f%n", (c >> 16 & 255) / 255.0, (c >> 8 & 255) / 255.0, (c & 255) / 255.0);
            if (MechPalette.glows(i)) w.printf("Ke %.4f %.4f %.4f%n", (c >> 16 & 255) / 255.0, (c >> 8 & 255) / 255.0, (c & 255) / 255.0);
         }
      }
   }
}
