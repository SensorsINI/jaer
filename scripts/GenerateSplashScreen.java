import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import javax.imageio.ImageIO;

/**
 * Overlays "jAER" and the full VERSION.txt string onto the splash base PNG and
 * writes 1024x1024 (macOS icns), 256x256 (Windows / installer wizard), and
 * 800x800 (install4j launcher splash + Java -splash) PNGs.
 *
 * Usage: GenerateSplashScreen &lt;base.png&gt; &lt;version&gt; &lt;out-1024.png&gt; &lt;out-256.png&gt; &lt;out-800.png&gt;
 */
public final class GenerateSplashScreen {

    private static final int SIZE_1024 = 1024;
    private static final int SIZE_800 = 800;
    private static final int SIZE_256 = 256;
    private static final Color GRADIENT_LEFT = new Color(0x7A, 0x5C, 0xC8);
    private static final Color GRADIENT_RIGHT = new Color(0x1E, 0x3A, 0x8A);
    private static final Color SHADOW = new Color(0, 0, 0, 140);

    private GenerateSplashScreen() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Usage: GenerateSplashScreen <base.png> <version> <out-1024.png> <out-256.png> <out-800.png>");
            System.exit(1);
        }
        File baseFile = new File(args[0]);
        String fullVersion = args[1].trim();
        File out1024 = new File(args[2]);
        File out256 = new File(args[3]);
        File out800 = new File(args[4]);
        if (!baseFile.isFile()) {
            throw new IllegalArgumentException("Base splash not found: " + baseFile.getAbsolutePath());
        }
        if (fullVersion.isEmpty()) {
            throw new IllegalArgumentException("Version string is empty");
        }

        BufferedImage base = ImageIO.read(baseFile);
        if (base == null) {
            throw new IllegalArgumentException("Could not read image: " + baseFile.getAbsolutePath());
        }

        BufferedImage canvas = new BufferedImage(SIZE_1024, SIZE_1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(base, 0, 0, SIZE_1024, SIZE_1024, null);
            drawTitle(g, "jAER", fullVersion);
        } finally {
            g.dispose();
        }

        Files.createDirectories(out1024.toPath().getParent());
        Files.createDirectories(out256.toPath().getParent());
        Files.createDirectories(out800.toPath().getParent());
        ImageIO.write(canvas, "png", out1024);
        writeScaled(canvas, SIZE_800, out800);
        writeScaled(canvas, SIZE_256, out256);

        System.out.println("Wrote " + out1024.getAbsolutePath() + ", " + out256.getAbsolutePath()
                + ", and " + out800.getAbsolutePath() + " (overlay jAER / " + fullVersion + ")");
    }

    private static void writeScaled(BufferedImage src, int size, File out) throws Exception {
        BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        ImageIO.write(dst, "png", out);
    }

    private static void drawTitle(Graphics2D g, String title, String version) {
        Font font = chooseBoldFont(Math.round(SIZE_1024 * 0.22f));
        FontRenderContext frc = g.getFontRenderContext();
        TextLayout titleLayout = new TextLayout(title, font, frc);
        TextLayout versionLayout = new TextLayout(version, font, frc);

        float titleW = titleLayout.getAdvance();
        float versionW = versionLayout.getAdvance();
        float maxW = Math.max(titleW, versionW);
        float lineGap = font.getSize2D() * 0.12f;
        float blockH = titleLayout.getAscent() + titleLayout.getDescent()
                + lineGap
                + versionLayout.getAscent() + versionLayout.getDescent();

        float cx = SIZE_1024 / 2f;
        float topY = (SIZE_1024 - blockH) / 2f;
        float titleBaseline = topY + titleLayout.getAscent();
        float versionBaseline = titleBaseline + titleLayout.getDescent() + lineGap + versionLayout.getAscent();

        // Soft shadow layer
        BufferedImage shadow = new BufferedImage(SIZE_1024, SIZE_1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = shadow.createGraphics();
        try {
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            sg.setPaint(SHADOW);
            float shadowDx = 10f;
            float shadowDy = 12f;
            titleLayout.draw(sg, cx - titleW / 2f + shadowDx, titleBaseline + shadowDy);
            versionLayout.draw(sg, cx - versionW / 2f + shadowDx, versionBaseline + shadowDy);
        } finally {
            sg.dispose();
        }
        BufferedImage blurred = blur(shadow, 7);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.drawImage(blurred, 0, 0, null);
        g.setComposite(AlphaComposite.SrcOver);

        // Gradient-filled glyphs via clip
        drawGradientText(g, titleLayout, cx - titleW / 2f, titleBaseline, maxW);
        drawGradientText(g, versionLayout, cx - versionW / 2f, versionBaseline, maxW);
    }

    private static void drawGradientText(Graphics2D g, TextLayout layout, float x, float baseline, float gradientWidth) {
        AffineTransform at = AffineTransform.getTranslateInstance(x, baseline);
        java.awt.Shape outline = layout.getOutline(at);
        Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gg.setClip(outline);
            // Horizontal purple -> blue across the text block, slightly translucent
            gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
            float left = x;
            float right = x + Math.max(layout.getAdvance(), gradientWidth * 0.5f);
            gg.setPaint(new java.awt.GradientPaint(left, 0, GRADIENT_LEFT, right, 0, GRADIENT_RIGHT));
            gg.fill(outline.getBounds2D());
        } finally {
            gg.dispose();
        }
    }

    private static Font chooseBoldFont(float size) {
        String[] preferred = {"Arial", "Helvetica", "DejaVu Sans", Font.SANS_SERIF};
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] available = ge.getAvailableFontFamilyNames();
        Arrays.sort(available);
        for (String name : preferred) {
            if (Font.SANS_SERIF.equals(name) || Arrays.binarySearch(available, name) >= 0) {
                return new Font(name, Font.BOLD, Math.round(size)).deriveFont(size);
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)).deriveFont(size);
    }

    private static BufferedImage blur(BufferedImage src, int radius) {
        if (radius < 1) {
            return src;
        }
        int size = radius * 2 + 1;
        float weight = 1f / (size * size);
        float[] data = new float[size * size];
        Arrays.fill(data, weight);
        Kernel kernel = new Kernel(size, size, data);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage padded = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = padded.createGraphics();
        try {
            pg.drawImage(src, 0, 0, null);
        } finally {
            pg.dispose();
        }
        return op.filter(padded, null);
    }
}
