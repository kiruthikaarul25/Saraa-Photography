package com.shop.service;

import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

@Service
public class WatermarkService {

    // Main method — returns watermarked image as byte array
    public byte[] addWatermark(String imagePath, String clientName) throws IOException {

        // 1. Load original image
        File file = new File(imagePath);
        BufferedImage original = ImageIO.read(file);
        if (original == null) throw new IOException("Cannot read image: " + imagePath);

        int width  = original.getWidth();
        int height = original.getHeight();

        // 2. Create a copy to draw on
        BufferedImage watermarked = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = watermarked.createGraphics();

        // Enable anti-aliasing for smooth text
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 3. Draw original image
        g.drawImage(original, 0, 0, null);

        // 4. Watermark settings
        int fontSize = Math.max(18, width / 30);  // scales with image size
        Font font = new Font("Arial", Font.BOLD, fontSize);
        g.setFont(font);

        FontMetrics fm = g.getFontMetrics();
        String watermarkText = "© Dream Gallery  |  " + clientName;

        // 5. Diagonal repeating watermark across full image
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f)); // 22% opacity
        g.setColor(Color.WHITE);

        // Rotate 45 degrees for diagonal
        g.rotate(Math.toRadians(-35), (double) width / 2, (double) height / 2);

        int textWidth  = fm.stringWidth(watermarkText);
        int textHeight = fm.getHeight();

        // Tile watermark across the entire (rotated) canvas
        int stepX = textWidth + 80;
        int stepY = textHeight + 60;

        // Extend range to cover corners after rotation
        int range = (int) Math.sqrt(width * width + height * height);

        for (int y = -range; y < range; y += stepY) {
            for (int x = -range; x < range; x += stepX) {
                g.drawString(watermarkText, x, y);
            }
        }

        // 6. Reset rotation, add bottom-right corner credit (non-rotated, semi-visible)
        g.setTransform(new java.awt.geom.AffineTransform());
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.setColor(Color.WHITE);
        int smallSize = Math.max(12, width / 60);
        g.setFont(new Font("Arial", Font.PLAIN, smallSize));
        FontMetrics fm2 = g.getFontMetrics();
        String credit = "© Dream Gallery Photography";
        int cx = width  - fm2.stringWidth(credit) - 12;
        int cy = height - 10;
        // Shadow
        g.setColor(new Color(0,0,0,120));
        g.drawString(credit, cx + 1, cy + 1);
        // Text
        g.setColor(new Color(255,255,255,180));
        g.drawString(credit, cx, cy);

        g.dispose();

        // 7. Output as JPEG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(watermarked, "jpg", baos);
        return baos.toByteArray();
    }
}