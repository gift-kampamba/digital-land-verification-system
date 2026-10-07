package com.landverification.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.landverification.config.AppProperties;
import com.landverification.dto.ParcelRegistrationRequest;
import com.landverification.model.LandParcel;
import com.landverification.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

@Service
@RequiredArgsConstructor
@Slf4j
public class TitleDeedService {

    // ── Canvas ────────────────────────────────────────────
    private static final int W = 1800;
    private static final int H = 2600;

    // ── Colours ───────────────────────────────────────────
    private static final Color CREAM      = new Color(247, 244, 234);
    private static final Color GREEN_DARK = new Color(7,  72,  31);
    private static final Color GREEN_MED  = new Color(12, 92,  40);
    private static final Color GOLD       = new Color(185, 148, 58);
    private static final Color RED_CERT   = new Color(166, 25,  25);
    private static final Color INK        = new Color(25,  25,  25);
    private static final Color INK_MID    = new Color(80,  80,  80);
    private static final Color SEAL_GREEN = new Color(10,  80,  35);

    private final AppProperties appProperties;

    // ─────────────────────────────────────────────────────
    // ENTRY POINT
    // ─────────────────────────────────────────────────────
    public String generateCertificate(
            LandParcel parcel,
            User owner,
            ParcelRegistrationRequest req
    ) throws Exception {

        Path deedFolder = Path.of(appProperties.getUploadDir(), "title-deeds");
        if (!Files.exists(deedFolder)) Files.createDirectories(deedFolder);

        String fileName = "title-deed-"
                + parcel.getParcelNumber().replaceAll("[^A-Za-z0-9_-]", "_")
                + "-" + UUID.randomUUID() + ".pdf";
        Path filePath = deedFolder.resolve(fileName);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        enableQuality(g);

        g.setColor(CREAM);
        g.fillRect(0, 0, W, H);
        drawPaperTexture(g);
        drawBorders(g);
        drawWatermark(g);
        drawHeader(g, parcel);
        drawBigTitle(g);
        drawDivider(g);
        drawPreamble(g);

        String qrContent = String.format(
                "%s/verify?parcelNumber=%s",
                appProperties.getBaseUrl().replaceAll("/+$", ""),
                URLEncoder.encode(parcel.getParcelNumber(), StandardCharsets.UTF_8));
        drawQrBox(g, createQrCode(qrContent, 320, 320));

        int y = 1105;
        y = drawSection(g, y, "1. PROPERTY DETAILS", new String[][]{
                {"PROVINCE",             safe(parcel.getProvince())},
                {"DISTRICT",             safe(parcel.getDistrict())},
                {"CONSTITUENCY",         "Lusaka Central"},
                {"AREA / LOCATION",      safe(parcel.getLocationAddress())},
                {"PLOT / PARCEL NUMBER", safe(parcel.getParcelNumber())},
                {"LAND USE",             parcel.getLandUse().name()},
                {"LAND SIZE",            parcel.getAreaSqm() + " Square Metres"},
                {"DEED PLAN NUMBER",     generateDeedPlanNumber(parcel)},
                {"GPS COORDINATES",      getGpsCoordinates(req)}
        });
        y += 28;

        y = drawSection(g, y, "2. REGISTERED PROPRIETOR", new String[][]{
                {"FULL NAME",               safe(owner.getFullName())},
                {"NATIONAL ID / PASSPORT",  safe(owner.getNationalId())},
                {"ADDRESS",                 safe(owner.getAddress())}
        });
        y += 28;

        drawSection(g, y, "3. TITLE INFORMATION", new String[][]{
                {"VOLUME NUMBER",     generateVolumeNumber(parcel)},
                {"FOLIO NUMBER",      generateFolioNumber(parcel)},
                {"DATE OF ISSUE",     LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))},
                {"ESTATE / INTEREST", "Freehold"},
                {"ENCUMBRANCES",      getEncumbrances(req)},
                {"REMARKS",           "None"}
        });

        drawSignatures(g);
        drawFooter(g);
        g.dispose();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDImageXObject pdImage = LosslessFactory.createFromImage(document, img);
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float margin = 36f;
            float availableWidth = pageWidth - margin * 2;
            float availableHeight = pageHeight - margin * 2;
            float scale = Math.min(availableWidth / W, availableHeight / H);
            float imageWidth = W * scale;
            float imageHeight = H * scale;
            float x = (pageWidth - imageWidth) / 2f;
            float yPos = (pageHeight - imageHeight) / 2f;

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImage, x, yPos, imageWidth, imageHeight);
            }

            document.save(filePath.toFile());
        }

        log.info("Title deed generated: {}", filePath);
        return "title-deeds/" + fileName;
    }

    // ─────────────────────────────────────────────────────
    // QUALITY
    // ─────────────────────────────────────────────────────
    private void enableQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    // ─────────────────────────────────────────────────────
    // PAPER TEXTURE
    // ─────────────────────────────────────────────────────
    private void drawPaperTexture(Graphics2D g) {
        Random rnd = new Random(42);
        for (int i = 0; i < 200_000; i++) {
            g.setColor(new Color(100, 90, 70, 2 + rnd.nextInt(5)));
            g.fillRect(rnd.nextInt(W), rnd.nextInt(H), 1, 1);
        }
    }

    // ─────────────────────────────────────────────────────
    // WATERMARK  — faint coat-of-arms centred in body
    // ─────────────────────────────────────────────────────
    private void drawWatermark(Graphics2D g) {
        Graphics2D wg = (Graphics2D) g.create();
        wg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.045f));
        int size = 900;
        BufferedImage coat = loadCoatOfArms();
        if (coat != null) {
            wg.drawImage(coat, (W - size) / 2, 1030, size, size, null);
        } else {
            drawCoatOfArms(wg, W / 2, 1080, 420, 420);
        }
        wg.dispose();
    }

    // ─────────────────────────────────────────────────────
    // LOAD COAT OF ARMS  — always from classpath, never drawn
    // ─────────────────────────────────────────────────────
    private BufferedImage loadCoatOfArms() {
        try (InputStream is = getClass().getResourceAsStream("/images/zambia-coat-of-arms.png")) {
            if (is == null) {
                log.warn("Zambia coat of arms image not found on classpath. Using vector fallback drawing instead.");
                return null;
            }
            return ImageIO.read(is);
        } catch (Exception e) {
            log.error("Failed to load coat of arms: {}", e.getMessage());
            return null;
        }
    }

    private void drawCoatOfArms(Graphics2D g, int cx, int y, int width, int height) {
        BufferedImage coatImage = loadCoatOfArms();
        if (coatImage != null) {
            // Preserve aspect ratio when drawing the provided PNG
            int iw = coatImage.getWidth();
            int ih = coatImage.getHeight();
            double scale = Math.min((double) width / iw, (double) height / ih);
            int dw = Math.max(1, (int) Math.round(iw * scale));
            int dh = Math.max(1, (int) Math.round(ih * scale));
            int dx = cx - dw / 2;
            int dy = y + (height - dh) / 2; // center vertically inside requested box
            Graphics2D ig = (Graphics2D) g.create();
            enableQuality(ig);
            ig.drawImage(coatImage, dx, dy, dw, dh, null);
            ig.dispose();
            return;
        }

        Graphics2D cg = (Graphics2D) g.create();
        enableQuality(cg);

        int shieldW = width * 50 / 64;
        int shieldH = height * 58 / 72;
        int shieldX = cx - shieldW / 2;
        int shieldY = y + 60;

        cg.setColor(new Color(37, 100, 52));
        int supporterW = 28;
        int supporterH = 170;
        cg.fillRoundRect(shieldX - 110, shieldY + 40, supporterW, supporterH, 16, 16);
        cg.fillRoundRect(shieldX + shieldW + 82, shieldY + 40, supporterW, supporterH, 16, 16);

        cg.setColor(GREEN_DARK);
        cg.fillRoundRect(shieldX - 12, shieldY - 8, shieldW + 24, shieldH + 16, 44, 44);
        cg.setColor(Color.WHITE);
        cg.fillRoundRect(shieldX, shieldY, shieldW, shieldH, 36, 36);

        int stripeCount = 6;
        int stripeW = shieldW / stripeCount;
        for (int i = 0; i < stripeCount; i++) {
            cg.setColor(i % 2 == 0 ? Color.BLACK : Color.WHITE);
            cg.fillRect(shieldX + i * stripeW, shieldY, stripeW, shieldH);
        }

        cg.setColor(GOLD);
        cg.fillOval(cx - 46, shieldY - 64, 92, 70);
        cg.setColor(RED_CERT);
        cg.fillOval(cx - 30, shieldY - 50, 60, 42);

        cg.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        cg.setColor(new Color(176, 75, 32));
        GeneralPath eagle = new GeneralPath();
        eagle.moveTo(cx - 92, shieldY - 16);
        eagle.curveTo(cx - 94, shieldY - 80, cx - 24, shieldY - 100, cx, shieldY - 78);
        eagle.curveTo(cx + 40, shieldY - 98, cx + 92, shieldY - 78, cx + 88, shieldY - 20);
        eagle.lineTo(cx + 88, shieldY + 10);
        eagle.curveTo(cx + 28, shieldY - 12, cx - 26, shieldY - 10, cx - 84, shieldY + 14);
        eagle.closePath();
        cg.draw(eagle);

        int bannerY = shieldY + shieldH + 28;
        cg.setColor(new Color(240, 233, 212));
        cg.fillRoundRect(cx - 190, bannerY, 380, 44, 32, 32);
        cg.setColor(GREEN_DARK);
        cg.setFont(new Font("Serif", Font.BOLD, 20));
        drawCenter(cg, "ONE ZAMBIA ONE NATION", cx, bannerY + 30);

        cg.dispose();
    }

    // ─────────────────────────────────────────────────────
    // BORDERS
    // ─────────────────────────────────────────────────────
    private void drawBorders(Graphics2D g) {
        // Outer thick dark-green
        g.setColor(GREEN_DARK);
        g.setStroke(new BasicStroke(14f));
        g.drawRect(16, 16, W - 32, H - 32);

        // Gold rule
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(3.5f));
        g.drawRect(38, 38, W - 76, H - 76);

        // Inner dark-green rule
        g.setColor(GREEN_DARK);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(54, 54, W - 108, H - 108);

        // Dotted inner guide
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{2f, 6f}, 0));
        g.drawRect(84, 84, W - 168, H - 168);

        // Corner ornaments
        g.setStroke(new BasicStroke(2f));
        drawCorner(g, 54,     54,     false, false);
        drawCorner(g, W - 54, 54,     true,  false);
        drawCorner(g, 54,     H - 54, false, true);
        drawCorner(g, W - 54, H - 54, true,  true);
    }

    private void drawCorner(Graphics2D g, int cx, int cy, boolean mx, boolean my) {
        Graphics2D cg = (Graphics2D) g.create();
        if (mx) cg.scale(-1, 1);
        if (my) cg.scale(1, -1);
        int ax = mx ? -cx : cx;
        int ay = my ? -cy : cy;
        cg.setColor(GREEN_DARK);
        cg.drawArc(ax, ay, 90, 90, 90, 90);
        cg.drawArc(ax + 12, ay + 12, 66, 66, 90, 90);
        cg.drawLine(ax + 45, ay, ax + 92, ay);
        cg.drawLine(ax, ay + 45, ax, ay + 92);
        cg.dispose();
    }

    // ─────────────────────────────────────────────────────
    // HEADER
    // ─────────────────────────────────────────────────────
    private void drawHeader(Graphics2D g, LandParcel parcel) {

        g.setColor(GREEN_DARK);
        g.setFont(new Font("Serif", Font.BOLD, 62));
        drawCenter(g, "REPUBLIC OF ZAMBIA", W / 2, 110);

        drawCoatOfArms(g, W / 2, 150, 320, 320);

        g.setColor(GREEN_DARK);
        g.setFont(new Font("Serif", Font.BOLD, 30));
        drawCenter(g, "MINISTRY OF LANDS AND NATURAL RESOURCES", W / 2, 534);

        g.setFont(new Font("Serif", Font.BOLD, 26));
        drawCenter(g, "LANDS AND DEEDS REGISTRATION DEPARTMENT", W / 2, 568);

        g.setColor(INK);
        g.setFont(new Font("Arial", Font.BOLD, 22));
        g.drawString("CERTIFICATE NO.", 1300, 172);

        g.setColor(RED_CERT);
        g.setFont(new Font("Arial", Font.BOLD, 32));
        String certNo = String.format("ZMB-LDR-%d-%08d",
                LocalDate.now().getYear(), parcel.getParcelId());
        g.drawString(certNo, 1280, 218);
        drawBarcode(g, 1280, 232, 360, 52);
    }

    // ─────────────────────────────────────────────────────
    // BIG TITLE
    // ─────────────────────────────────────────────────────
    private void drawBigTitle(Graphics2D g) {
        g.setColor(GREEN_DARK);
        g.setFont(new Font("Serif", Font.BOLD, 118));
        drawCenter(g, "CERTIFICATE OF TITLE", W / 2, 718);
    }

    // ─────────────────────────────────────────────────────
    // GOLD ORNAMENTAL DIVIDER
    // ─────────────────────────────────────────────────────
    private void drawDivider(Graphics2D g) {
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.8f));
        int y = 800, cx = W / 2;
        g.drawLine(200, y, 710, y);
        g.drawLine(1090, y, 1600, y);
        // small overlapping ovals as central ornament
        for (int i = 0; i < 6; i++) {
            g.drawOval(cx - 90 + i * 26, y - 14, 28, 28);
        }
    }

    // ─────────────────────────────────────────────────────
    // PREAMBLE
    // ─────────────────────────────────────────────────────
    private void drawPreamble(Graphics2D g) {
        int cx = 610;
        g.setColor(GREEN_DARK);
        g.setFont(new Font("Serif", Font.BOLD, 33));
        drawCenter(g, "THIS IS TO CERTIFY THAT", cx, 900);

        g.setColor(INK);
        g.setFont(new Font("Serif", Font.PLAIN, 27));
        drawCenter(g, "The person named herein is the registered proprietor of the", cx, 958);
        drawCenter(g, "land described below, subject to the provisions of the Lands",  cx, 998);
        drawCenter(g, "and Deeds Registration Act, Cap 185 of the Laws of Zambia",    cx, 1038);
        drawCenter(g, "and any overriding interests that may exist.",                  cx, 1078);
    }

    // ─────────────────────────────────────────────────────
    // QR BOX
    // ─────────────────────────────────────────────────────
    private void drawQrBox(Graphics2D g, BufferedImage qr) {
        int x = 1220, y = 848, size = 370, pad = 24;
        g.setColor(GREEN_DARK);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, size, size, 16, 16);
        g.drawImage(qr, x + pad, y + pad, size - 2 * pad, size - 2 * pad, null);
    }

    // ─────────────────────────────────────────────────────
    // DATA SECTION
    // ─────────────────────────────────────────────────────
    private int drawSection(Graphics2D g, int y, String title, String[][] rows) {
        // Pill header
        g.setColor(GREEN_MED);
        g.fillRoundRect(108, y, 400, 46, 20, 20);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 21));
        g.drawString(title, 132, y + 30);

        // Rule
        g.setColor(new Color(160, 155, 135));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(520, y + 23, 1680, y + 23);

        int ry = y + 78;
        for (String[] row : rows) {
            g.setColor(INK);
            g.setFont(new Font("Arial", Font.BOLD, 21));
            g.drawString(row[0], 118, ry);
            g.drawString(":", 510, ry);
            g.setFont(new Font("Arial", Font.PLAIN, 21));
            g.drawString(row[1] != null ? row[1] : "N/A", 548, ry);
            ry += 52;
        }
        return ry;
    }

    // ─────────────────────────────────────────────────────
    // SIGNATURES + SEAL
    // ─────────────────────────────────────────────────────
    private void drawSignatures(Graphics2D g) {
        g.setColor(INK);
        g.setStroke(new BasicStroke(1.5f));

        int signatureAreaHeight = 170;
        int leftCenter = 360;
        int rightCenter = 1450;
        int topY = 2200;
        int lineY = topY + signatureAreaHeight + 40;

        boolean leftImageDrawn = drawSignatureImage(g, "cheif lands officer.png", leftCenter, topY, 420, signatureAreaHeight);
        if (!leftImageDrawn) {
            drawCursiveSignature(g, leftCenter, topY + 30, true);
        }
        g.drawLine(leftCenter - 210, lineY, leftCenter + 210, lineY);
        g.setFont(new Font("Arial", Font.BOLD, 21));
        drawCenter(g, "CHIEF LANDS OFFICER", leftCenter, lineY + 38);
        g.setFont(new Font("Arial", Font.PLAIN, 19));
        drawCenter(g, "For / Registrar of Lands and Deeds", leftCenter, lineY + 68);

        boolean rightImageDrawn = drawSignatureImage(g, "registrar of lands.png", rightCenter, topY, 420, signatureAreaHeight);
        if (!rightImageDrawn) {
            drawCursiveSignature(g, rightCenter, topY + 30, false);
        }
        g.drawLine(rightCenter - 210, lineY, rightCenter + 210, lineY);
        g.setFont(new Font("Arial", Font.BOLD, 21));
        drawCenter(g, "REGISTRAR OF LANDS AND DEEDS", rightCenter, lineY + 38);
        g.setFont(new Font("Arial", Font.PLAIN, 19));
        drawCenter(g, "Ministry of Lands and Natural Resources", rightCenter, lineY + 68);

        drawOfficialSeal(g, W / 2, lineY - 80);
    }

    private boolean drawSignatureImage(Graphics2D g, String resourceName, int cx, int topY, int maxWidth, int maxHeight) {
        BufferedImage signature = loadImageResource("/images/" + resourceName);
        if (signature == null) {
            return false;
        }
        int iw = signature.getWidth();
        int ih = signature.getHeight();
        double scale = Math.min((double) maxWidth / iw, (double) maxHeight / ih);
        int dw = Math.max(1, (int) Math.round(iw * scale));
        int dh = Math.max(1, (int) Math.round(ih * scale));
        int dx = cx - dw / 2;
        int dy = topY + (maxHeight - dh) / 2;
        Graphics2D ig = (Graphics2D) g.create();
        enableQuality(ig);
        ig.drawImage(signature, dx, dy, dw, dh, null);
        ig.dispose();
        return true;
    }

    private BufferedImage loadImageResource(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Image resource not found: {}", resourcePath);
                return null;
            }
            return ImageIO.read(is);
        } catch (Exception e) {
            log.error("Failed to load image resource {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    private void drawCursiveSignature(Graphics2D g, int cx, int baseY, boolean left) {
        Graphics2D sg = (Graphics2D) g.create();
        sg.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        sg.setColor(INK);
        GeneralPath p = new GeneralPath();
        if (left) {
            p.moveTo(cx - 100, baseY + 50);
            p.curveTo(cx - 60, baseY,     cx - 20, baseY + 20, cx,      baseY + 10);
            p.curveTo(cx + 20, baseY,     cx + 50, baseY + 30, cx + 80, baseY + 10);
            p.curveTo(cx + 95, baseY,     cx + 100, baseY - 10, cx + 110, baseY + 5);
        } else {
            p.moveTo(cx - 110, baseY + 45);
            p.curveTo(cx - 70, baseY + 5, cx - 30, baseY + 25, cx,      baseY + 10);
            p.curveTo(cx + 25, baseY - 5, cx + 55, baseY + 20, cx + 85, baseY + 5);
            p.curveTo(cx + 95, baseY - 5, cx + 105, baseY - 15, cx + 115, baseY);
        }
        sg.draw(p);
        sg.dispose();
    }

    // ─────────────────────────────────────────────────────
    // OFFICIAL SEAL  — serrated ring + arc text + star
    // ─────────────────────────────────────────────────────
    private void drawOfficialSeal(Graphics2D g, int cx, int cy) {
        Graphics2D sg = (Graphics2D) g.create();
        enableQuality(sg);

        int outerR = 120, innerR = 95, coreR = 72;

        // Gear teeth
        sg.setColor(SEAL_GREEN);
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10.0);
            double a1 = Math.toRadians(i * 10.0 - 4);
            double a2 = Math.toRadians(i * 10.0 + 4);
            GeneralPath tooth = new GeneralPath();
            tooth.moveTo(cx + Math.cos(a1) * innerR, cy + Math.sin(a1) * innerR);
            tooth.lineTo(cx + Math.cos(angle) * outerR, cy + Math.sin(angle) * outerR);
            tooth.lineTo(cx + Math.cos(a2) * innerR, cy + Math.sin(a2) * innerR);
            tooth.closePath();
            sg.fill(tooth);
        }
        sg.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        sg.setColor(CREAM);
        sg.fillOval(cx - innerR + 6, cy - innerR + 6, (innerR - 6) * 2, (innerR - 6) * 2);

        sg.setColor(SEAL_GREEN);
        sg.fillOval(cx - coreR, cy - coreR, coreR * 2, coreR * 2);

        // Arc text
        sg.setColor(Color.WHITE);
        sg.setFont(new Font("Arial", Font.BOLD, 18));
        drawArcText(sg, "MINISTRY OF LANDS", cx, cy, 62, Math.toRadians(-165), true);
        drawArcText(sg, "NATURAL RESOURCES", cx, cy, 62, Math.toRadians(15),  false);

        // Star
        sg.setColor(Color.WHITE);
        drawStar(sg, cx, cy + 35, 14);

        // Text
        sg.setFont(new Font("Arial", Font.BOLD, 18));
        drawCenter(sg, "OFFICIAL", cx, cy + 2);
        drawCenter(sg, "SEAL",     cx, cy + 25);

        sg.dispose();
    }

    private void drawArcText(Graphics2D g, String text, int cx, int cy,
                              int radius, double startAngle, boolean clockwise) {
        double step = Math.toRadians(9.5);
        for (int i = 0; i < text.length(); i++) {
            double a = clockwise ? startAngle + i * step : startAngle - i * step;
            float tx = (float)(cx + radius * Math.cos(a));
            float ty = (float)(cy + radius * Math.sin(a));
            Graphics2D cg = (Graphics2D) g.create();
            cg.translate(tx, ty);
            cg.rotate(clockwise ? a + Math.PI / 2 : a - Math.PI / 2);
            cg.drawString(String.valueOf(text.charAt(i)), -5, 0);
            cg.dispose();
        }
    }

    private void drawStar(Graphics2D g, int cx, int cy, int r) {
        GeneralPath star = new GeneralPath();
        int rInner = r / 2, points = 5;
        for (int i = 0; i < points * 2; i++) {
            double a = Math.toRadians(i * 180.0 / points - 90);
            double rad = (i % 2 == 0) ? r : rInner;
            float x = (float)(cx + rad * Math.cos(a));
            float y = (float)(cy + rad * Math.sin(a));
            if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.closePath();
        g.fill(star);
    }

    // ─────────────────────────────────────────────────────
    // FOOTER
    // ─────────────────────────────────────────────────────
    private void drawFooter(Graphics2D g) {
        g.setColor(new Color(232, 226, 210));
        g.fillRoundRect(340, 2430, 1120, 72, 10, 10);
        g.setColor(new Color(180, 172, 145));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(340, 2430, 1120, 72, 10, 10);
        g.setColor(INK_MID);
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        drawCenter(g, "This certificate is a computer generated document.", W / 2, 2457);
        drawCenter(g, "Alteration, forgery or misuse is an offence under the Laws of the Republic of Zambia.",
                W / 2, 2487);
    }

    // ─────────────────────────────────────────────────────
    // BARCODE
    // ─────────────────────────────────────────────────────
    private void drawBarcode(Graphics2D g, int x, int y, int w, int h) {
        Random rnd = new Random(12345);
        int pos = x;
        g.setColor(Color.BLACK);
        while (pos < x + w) {
            int bw = 1 + rnd.nextInt(4);
            if (rnd.nextBoolean()) g.fillRect(pos, y, bw, h);
            pos += bw + 1;
        }
    }

    // ─────────────────────────────────────────────────────
    // QR CODE
    // ─────────────────────────────────────────────────────
    private BufferedImage createQrCode(String text, int width, int height) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter()
                .encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    // ─────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────
    private void drawCenter(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }

    private String safe(String v) {
        return (v == null || v.isBlank()) ? "Not Provided" : v;
    }

    private String generateDeedPlanNumber(LandParcel parcel) {
        String pn   = parcel.getParcelNumber().replaceAll("[^A-Za-z0-9]", "");
        String area = parcel.getLocationAddress() != null
                ? parcel.getLocationAddress().toUpperCase().replaceAll("\\s+", "") : "PRP";
        return String.format("LSK/%s/%s/DP/%d", area, pn, LocalDate.now().getYear());
    }

    private String generateVolumeNumber(LandParcel parcel) {
        return "LRV " + (1000 + (parcel.getParcelId() % 1000));
    }

    private String generateFolioNumber(LandParcel parcel) {
        return String.format("%03d", parcel.getParcelId() % 200);
    }

    private String getGpsCoordinates(ParcelRegistrationRequest req) {
        return (req.getGpsLat() != null && req.getGpsLng() != null
                && !req.getGpsLat().isBlank() && !req.getGpsLng().isBlank())
                ? req.getGpsLat() + ", " + req.getGpsLng()
                : "Not Provided";
    }

    private String getEncumbrances(ParcelRegistrationRequest req) {
        return (req.getEncumbrances() != null && !req.getEncumbrances().isBlank())
                ? req.getEncumbrances()
                : "No encumbrances";
    }
}