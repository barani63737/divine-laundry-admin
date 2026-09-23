package com.divinelaundry.service;

import com.divinelaundry.domain.OrderItem;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InvoicePaymentImageService {
    private static final int WIDTH = 1200;
    private static final Color GREEN = new Color(7, 89, 56);
    private static final Color LIGHT_GREEN = new Color(229, 245, 237);
    private static final Color INK = new Color(23, 35, 29);
    private static final Color MUTED = new Color(95, 113, 104);
    private static final Color LINE = new Color(225, 233, 228);
    private static final Color DANGER = new Color(182, 65, 65);
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
    private static final Locale INDIA = new Locale("en", "IN");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final String businessZone;
    private final String upiId;
    private final String upiPayeeName;

    public InvoicePaymentImageService(
            @Value("${app.business-zone:Asia/Kolkata}") String businessZone,
            @Value("${app.payment.upi-id:}") String upiId,
            @Value("${app.payment.upi-payee-name:Divine Laundry Trichy}") String upiPayeeName) {
        this.businessZone = businessZone;
        this.upiId = upiId == null ? "" : upiId.trim();
        this.upiPayeeName = upiPayeeName == null ? "Divine Laundry Trichy" : upiPayeeName.trim();
    }

    public byte[] render(DocumentService.DocumentBundle bundle) {
        List<OrderItem> items = bundle.order().getItems();
        int displayedRows = items.size();
        int height = 1130 + displayedRows * 65;
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            prepare(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, height);
            drawHeader(graphics, bundle);
            drawCustomer(graphics, bundle);
            int tableEnd = drawItems(graphics, items, displayedRows);
            drawPayment(graphics, bundle, tableEnd, height);
            drawFooter(graphics, bundle, height);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not create the invoice PNG", error);
        }
    }

    private void drawHeader(Graphics2D graphics, DocumentService.DocumentBundle bundle) {
        graphics.setColor(GREEN);
        graphics.fillRect(0, 0, WIDTH, 170);
        graphics.setColor(Color.WHITE);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 46f));
        fitted(graphics, bundle.business().name(), 60, 72, 650);
        graphics.setFont(FONT.deriveFont(22f));
        fitted(graphics, bundle.business().address(), 60, 112, 650);
        fitted(graphics, businessPhone(bundle.business().phone()), 60, 145, 650);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 27f));
        right(graphics, "INVOICE + PAYMENT", WIDTH - 60, 63);
        graphics.setFont(FONT.deriveFont(22f));
        right(graphics, bundle.order().getInvoiceNumber(), WIDTH - 60, 105);
        right(graphics, bundle.order().getOrderNumber(), WIDTH - 60, 140);
    }

    private void drawCustomer(Graphics2D graphics, DocumentService.DocumentBundle bundle) {
        int y = 220;
        graphics.setColor(INK);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 22f));
        graphics.drawString("BILL TO", 60, y);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 31f));
        fitted(graphics, bundle.order().getCustomer().getName(), 60, y + 45, 550);
        graphics.setFont(FONT.deriveFont(21f));
        graphics.setColor(MUTED);
        fitted(graphics, bundle.order().getCustomer().getPhone(), 60, y + 80, 550);
        String address = String.join(", ", List.of(
                        value(bundle.order().getCustomer().getAddressLine()),
                        value(bundle.order().getCustomer().getArea())))
                .replaceAll("^, |, $", "");
        fitted(graphics, address.isBlank() ? "Address not set" : address, 60, y + 115, 550);

        graphics.setColor(INK);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 21f));
        graphics.drawString("PLACED", 720, y);
        graphics.setFont(FONT.deriveFont(21f));
        graphics.setColor(MUTED);
        graphics.drawString(dateTime(bundle.order().getPlacedAt()), 720, y + 40);
        graphics.setColor(INK);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 21f));
        graphics.drawString("DELIVERY", 720, y + 82);
        graphics.setFont(FONT.deriveFont(21f));
        graphics.setColor(MUTED);
        graphics.drawString(dateTime(bundle.order().getDeliveryAt()), 720, y + 117);
    }

    private int drawItems(Graphics2D graphics, List<OrderItem> items, int displayedRows) {
        int tableTop = 385;
        graphics.setColor(LIGHT_GREEN);
        graphics.fillRoundRect(45, tableTop, WIDTH - 90, 55, 12, 12);
        graphics.setColor(GREEN);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 19f));
        graphics.drawString("SERVICE", 65, tableTop + 36);
        graphics.drawString("QTY", 650, tableTop + 36);
        graphics.drawString("PCS", 790, tableTop + 36);
        right(graphics, "AMOUNT", WIDTH - 65, tableTop + 36);
        int y = tableTop + 93;
        graphics.setFont(FONT.deriveFont(21f));
        for (int index = 0; index < displayedRows; index++) {
            OrderItem item = items.get(index);
            graphics.setColor(INK);
            fitted(graphics, item.getServiceName(), 65, y, 535);
            graphics.setFont(FONT.deriveFont(16f));
            fitted(graphics, "Rate: " + money(item.getUnitRate()) + " / " + item.getPricingUnit(), 65, y + 21, 535);
            graphics.setFont(FONT.deriveFont(21f));
            graphics.setColor(MUTED);
            graphics.drawString(item.getBillableQuantity().stripTrailingZeros().toPlainString()
                    + " " + item.getPricingUnit().name().toLowerCase(Locale.ENGLISH), 650, y);
            graphics.drawString(String.valueOf(item.getPieceCount()), 790, y);
            right(graphics, money(item.getLineTotal()), WIDTH - 65, y);
            graphics.setColor(LINE);
            graphics.drawLine(55, y + 25, WIDTH - 55, y + 25);
            y += 65;
        }
        if (items.size() > displayedRows) {
            graphics.setColor(MUTED);
            graphics.setFont(FONT.deriveFont(Font.ITALIC, 18f));
            graphics.drawString("+ " + (items.size() - displayedRows) + " additional service lines", 65, y);
            y += 45;
        }
        return y;
    }

    private void drawPayment(Graphics2D graphics, DocumentService.DocumentBundle bundle, int top, int imageHeight) {
        BigDecimal paid = bundle.paymentSummary().amountPaid();
        BigDecimal balance = bundle.paymentSummary().balance();
        graphics.setColor(MUTED);
        graphics.setFont(FONT.deriveFont(18f));
        fitted(graphics, "Subtotal: " + money(bundle.order().getSubtotal()) + "   Discount: " + money(bundle.order().getDiscount())
                + "   Tax: " + money(bundle.order().getTax()) + "   Round-off: " + money(bundle.order().getRoundOff()), 60, top + 20, WIDTH - 120);
        int panelTop = top + 55;
        int panelHeight = Math.min(390, imageHeight - panelTop - 105);
        graphics.setColor(new Color(247, 250, 248));
        graphics.fillRoundRect(45, panelTop, WIDTH - 90, panelHeight, 18, 18);

        graphics.setColor(INK);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 24f));
        graphics.drawString("PAYMENT DETAILS", 75, panelTop + 48);
        graphics.setFont(FONT.deriveFont(22f));
        summaryRow(graphics, "Invoice total", bundle.order().getTotal(), 75, panelTop + 95, 540);
        summaryRow(graphics, "Amount paid", paid, 75, panelTop + 145, 540);
        graphics.setFont(FONT.deriveFont(Font.BOLD, 27f));
        graphics.setColor(balance.signum() > 0 ? DANGER : GREEN);
        graphics.drawString("Balance", 75, panelTop + 207);
        right(graphics, money(balance), 540, panelTop + 207);
        graphics.setFont(FONT.deriveFont(19f));
        graphics.setColor(MUTED);
        fitted(graphics, "Status: " + bundle.order().getPaymentStatus().name(), 75, panelTop + 250, 465);

        if (balance.signum() > 0 && upiId.isBlank()) {
            graphics.setColor(MUTED);
            graphics.setFont(FONT.deriveFont(22f));
            graphics.drawString("Payment QR not configured", 665, panelTop + 135);
            graphics.setFont(FONT.deriveFont(18f));
            graphics.drawString("Please contact the shop to pay.", 665, panelTop + 175);
        } else if (balance.signum() > 0) {
            ensureUpiConfigured();
            int qrSize = Math.min(295, panelHeight - 62);
            int qrX = WIDTH - 75 - qrSize;
            int qrY = panelTop + 25;
            drawQr(graphics, upiPaymentUri(bundle, balance), qrX, qrY, qrSize);
            graphics.setColor(INK);
            graphics.setFont(FONT.deriveFont(Font.BOLD, 19f));
            fitted(graphics, "Scan to pay " + money(balance), 610, qrY + qrSize + 30, 515);
            graphics.setFont(FONT.deriveFont(16f));
            graphics.setColor(MUTED);
            fitted(graphics, "UPI: " + upiId, 610, qrY + qrSize + 56, 515);
        } else {
            graphics.setColor(LIGHT_GREEN);
            graphics.fillRoundRect(650, panelTop + 75, 440, 150, 18, 18);
            graphics.setColor(GREEN);
            graphics.setFont(FONT.deriveFont(Font.BOLD, 38f));
            graphics.drawString("PAID IN FULL", 735, panelTop + 145);
            graphics.setFont(FONT.deriveFont(19f));
            graphics.drawString("No balance payment required", 720, panelTop + 188);
        }
    }

    String upiPaymentUri(DocumentService.DocumentBundle bundle, BigDecimal balance) {
        ensureUpiConfigured();
        return "upi://pay?pa=" + encode(upiId)
                + "&pn=" + encode(upiPayeeName)
                + "&am=" + balance.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "&cu=INR"
                + "&tr=" + encode(bundle.order().getOrderNumber())
                + "&tn=" + encode("Laundry invoice " + bundle.order().getInvoiceNumber());
    }

    private void drawFooter(Graphics2D graphics, DocumentService.DocumentBundle bundle, int height) {
        graphics.setColor(MUTED);
        graphics.setFont(FONT.deriveFont(17f));
        fitted(graphics, "Please verify the shop name, UPI ID and amount in your payment app before paying.", 55, height - 60, WIDTH - 110);
        fitted(graphics, "Thank you for choosing " + bundle.business().name() + ".", 55, height - 30, WIDTH - 110);
    }

    private void summaryRow(Graphics2D graphics, String label, BigDecimal amount, int x, int y, int rightX) {
        graphics.setColor(MUTED);
        graphics.drawString(label, x, y);
        graphics.setColor(INK);
        right(graphics, money(amount), rightX, y);
    }

    private static void drawQr(Graphics2D graphics, String contents, int x, int y, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, size, size, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2,
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
            graphics.setColor(Color.WHITE);
            graphics.fillRect(x, y, size, size);
            graphics.setColor(Color.BLACK);
            for (int row = 0; row < matrix.getHeight(); row++) {
                for (int column = 0; column < matrix.getWidth(); column++) {
                    if (matrix.get(column, row)) graphics.fillRect(x + column, y + row, 1, 1);
                }
            }
        } catch (WriterException error) {
            throw new IllegalStateException("Could not generate the UPI QR code", error);
        }
    }

    private String dateTime(java.time.Instant value) {
        if (value == null) return "Not set";
        return DATE_TIME.format(value.atZone(ZoneId.of(businessZone)));
    }

    private void ensureUpiConfigured() {
        if (upiId.isBlank() || !upiId.contains("@")) {
            throw new IllegalStateException("A verified UPI_ID is required before sending payment QR codes");
        }
    }

    private static void prepare(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static void fitted(Graphics2D graphics, String text, int x, int y, int maxWidth) {
        String value = text == null ? "" : text;
        FontMetrics metrics = graphics.getFontMetrics();
        while (metrics.stringWidth(value) > maxWidth && value.length() > 3) {
            value = value.substring(0, value.length() - 2) + "…";
        }
        graphics.drawString(value, x, y);
    }

    private static void right(Graphics2D graphics, String text, int rightX, int y) {
        graphics.drawString(text, rightX - graphics.getFontMetrics().stringWidth(text), y);
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(INDIA).format(value == null ? BigDecimal.ZERO : value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }

    private static String businessPhone(String input) {
        return value(input).isBlank() ? "Phone not configured" : value(input);
    }
}
