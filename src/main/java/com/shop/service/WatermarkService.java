package com.shop.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Creates watermarked HD previews + thumbnails ONCE and caches them on disk
 * (uploads/previews). Originals are never changed.
 */
@Service
public class WatermarkService {

    /** HD preview: long edge 2400 px, JPEG quality 90% */
    private static final int HD_MAX = 2400;
    /** Grid thumbnail: long edge 700 px, JPEG quality 82% */
    private static final int THUMB_MAX = 700;

    private static final Path CACHE_DIR =
            Paths.get(System.getProperty("user.dir"), "uploads", "previews");

    // Decode at most N images at the same time (N = CPU cores, min 2)
    private static final Semaphore LIMIT =
            new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors()));

    /** Old method name kept (HD preview bytes). */
    public byte[] addWatermark(String fullPath, String clientName) throws IOException {
        return getPreview(fullPath, false);
    }

    /** Bytes version (kept for old callers). */
    public byte[] getPreview(String fullPath, boolean thumb) throws IOException {
        return Files.readAllBytes(getPreviewPath(fullPath, thumb));
    }

    /** Returns the cached file path (creates the preview if missing). */
    public Path getPreviewPath(String fullPath, boolean thumb) throws IOException {
        File imageFile = new File(fullPath);
        if (!imageFile.exists()) {
            throw new IOException("Photo not found: " + fullPath);
        }

        Files.createDirectories(CACHE_DIR);
        String key = Long.toHexString(fullPath.hashCode() & 0xffffffffL) + "_"
                + Long.toHexString(imageFile.lastModified()) + "_"
                + Long.toHexString(imageFile.length()) + (thumb ? "_t" : "_hd") + ".jpg";
        Path cached = CACHE_DIR.resolve(key);

        if (Files.exists(cached)) {
            return cached;
        }

        byte[] bytes;
        try {
            LIMIT.acquire();
            try {
                if (Files.exists(cached)) {
                    return cached; // another thread finished first
                }
                bytes = render(imageFile, thumb ? THUMB_MAX : HD_MAX, thumb ? 0.82f : 0.90f, !thumb);
            } finally {
                LIMIT.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }

        // temp file then move (safe when 2 requests create the same preview)
        Path tmp = CACHE_DIR.resolve(key + "." + Thread.currentThread().getId() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
        }
        return cached;
    }

    /**
     * Background pre-generation. Call from ANOTHER bean (e.g. upload controller),
     * otherwise @Async will not work.
     */
    @Async("previewExecutor")
    public void pregenerate(String fullPath) {
        try {
            getPreviewPath(fullPath, true);  // thumbnail first (grid needs it)
            getPreviewPath(fullPath, false); // then HD
        } catch (Exception ignored) {
            // preview will be created on first view instead
        }
    }

    private byte[] render(File imageFile, int maxDim, float quality, boolean watermark)
            throws IOException {

        BufferedImage original;
        try (ImageInputStream in = ImageIO.createImageInputStream(imageFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("Unable to read image file");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w0 = reader.getWidth(0);
                int h0 = reader.getHeight(0);
                // thumb: decode only what is needed. HD: keep 2x headroom for quality.
                int target = watermark ? maxDim * 2 : maxDim;
                int sub = Math.max(1, Math.max(w0, h0) / target);
                ImageReadParam param = reader.getDefaultReadParam();
                if (sub > 1) {
                    param.setSourceSubsampling(sub, sub, 0, 0);
                }
                original = reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
        if (original == null) {
            throw new IOException("Unable to read image file");
        }

        int ow = original.getWidth();
        int oh = original.getHeight();
        double scale = Math.min(1.0, (double) maxDim / Math.max(ow, oh));
        int width = Math.max(1, (int) Math.round(ow * scale));
        int height = Math.max(1, (int) Math.round(oh * scale));

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                watermark ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
                          : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                watermark ? RenderingHints.VALUE_RENDER_QUALITY
                          : RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(original, 0, 0, width, height, null);
        original.flush();

        if (watermark) {
            String text = "\u00a9 DREAM GALLERY";
            int fontSize = Math.max(16, width / 35);
            Font font = new Font("Arial", Font.BOLD, fontSize);
            g.setFont(font);
            while (g.getFontMetrics().stringWidth(text) > width * 0.80 && fontSize > 10) {
                fontSize--;
                font = new Font("Arial", Font.BOLD, fontSize);
                g.setFont(font);
            }
            int x = (width - g.getFontMetrics().stringWidth(text)) / 2;
            int y = height / 2;

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
            g.setColor(Color.BLACK);
            g.drawString(text, x + 1, y + 1);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
            g.setColor(Color.WHITE);
            g.drawString(text, x, y);
        }
        g.dispose();

        return toJpeg(out, quality);
    }

    private byte[] toJpeg(BufferedImage img, float quality) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), p);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }
}