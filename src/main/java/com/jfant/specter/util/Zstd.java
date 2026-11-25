package com.jfant.specter.util;

/**
 * Reads a Zstandard frame far enough to get at data that was never really compressed.
 *
 * <p>Servers write small strings into item data through a Zstandard frame - DonutSMP puts a map's coordinates in one.
 * Zstandard does not compress a payload that small: it stores it as one raw block, text and all. So the frame header
 * and the block headers are read properly (RFC 8878), raw and run-length blocks are unpacked, and a block that really
 * is compressed - Huffman literals and sequences, a decompressor in its own right - is refused rather than guessed at.
 * Neither Minecraft nor Fabric ships a Zstandard library, and a whole one is not worth carrying for this.
 *
 * <p>Nothing here trusts its input: every length is checked against what is left, and the output is capped.
 */
public final class Zstd {
    private Zstd() {}

    /** 0xFD2FB528, little-endian, at the start of every frame. */
    private static final int MAGIC = 0xFD2FB528;
    /** Far more than anything a server writes onto an item, and a limit on what a run-length block can claim. */
    private static final int MAX_OUTPUT = 1 << 16;

    private static final int RAW = 0, RLE = 1, COMPRESSED = 2;

    /**
     * The frame's contents, or null when there is no frame here, it is damaged, or a block in it is truly compressed.
     * Never throws.
     */
    public static byte[] read(byte[] frame) {
        if (frame == null || frame.length < 9) return null;

        try {
            if (u32(frame, 0) != MAGIC) return null;

            int at = 4;
            int descriptor = frame[at++] & 0xFF;
            int contentSizeFlag = descriptor >> 6 & 3;
            boolean singleSegment = (descriptor & 0x20) != 0;
            if ((descriptor & 0x08) != 0) return null; // Reserved bit, which has to be zero.
            boolean checksum = (descriptor & 0x04) != 0;
            int dictionaryFlag = descriptor & 3;

            // The window descriptor is left out of a single-segment frame: its one block is the whole of it.
            if (!singleSegment) at++;
            at += dictionaryFlag == 3 ? 4 : dictionaryFlag;
            at += switch (contentSizeFlag) {
                case 0 -> singleSegment ? 1 : 0;
                case 1 -> 2;
                case 2 -> 4;
                default -> 8;
            };
            if (at > frame.length) return null;

            byte[] out = new byte[0];
            while (true) {
                if (at + 3 > frame.length) return null;
                int header = (frame[at] & 0xFF) | (frame[at + 1] & 0xFF) << 8 | (frame[at + 2] & 0xFF) << 16;
                at += 3;

                boolean last = (header & 1) != 0;
                int type = header >> 1 & 3;
                int size = header >>> 3;
                if (type == COMPRESSED || type > COMPRESSED) return null;
                if (size < 0 || out.length + size > MAX_OUTPUT) return null;

                int from = out.length;
                if (type == RAW) {
                    if (at + size > frame.length) return null;
                    out = grow(out, size);
                    System.arraycopy(frame, at, out, from, size);
                    at += size;
                } else {
                    // Run-length: one byte, repeated.
                    if (at + 1 > frame.length) return null;
                    byte value = frame[at++];
                    out = grow(out, size);
                    for (int i = from; i < out.length; i++) out[i] = value;
                }

                if (last) break;
            }

            if (checksum && at + 4 > frame.length) return null;
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The frame's contents as text, trimmed; null when there is nothing readable in it. */
    public static String text(byte[] frame) {
        byte[] out = read(frame);
        if (out == null || out.length == 0) return null;
        String text = new String(out, java.nio.charset.StandardCharsets.UTF_8).trim();
        return text.isEmpty() ? null : text;
    }

    private static byte[] grow(byte[] out, int by) {
        byte[] bigger = new byte[out.length + by];
        System.arraycopy(out, 0, bigger, 0, out.length);
        return bigger;
    }

    private static int u32(byte[] b, int at) {
        return b[at] & 0xFF | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | (b[at + 3] & 0xFF) << 24;
    }
}
