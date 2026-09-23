package com.divinelaundry.service;

import com.divinelaundry.domain.OrderItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class PdfInvoiceService {
    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float LEFT = 42;
    private static final float TOP = PAGE.getHeight() - 42;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final ZoneId businessZone;
    private final String upiId;
    private final String upiPayeeName;

    public PdfInvoiceService(@org.springframework.beans.factory.annotation.Value("${app.business-zone:Asia/Kolkata}") String businessZone,
            @org.springframework.beans.factory.annotation.Value("${app.payment.upi-id:}") String upiId,
            @org.springframework.beans.factory.annotation.Value("${app.payment.upi-payee-name:Divine Laundry Trichy}") String upiPayeeName) {
        this.businessZone = ZoneId.of(businessZone);
        this.upiId = upiId == null ? "" : upiId.trim();
        this.upiPayeeName = upiPayeeName == null ? "" : upiPayeeName.trim();
    }

    public byte[] render(DocumentService.DocumentBundle document) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPageContentStream content = null;
            float y = 0;
            try {
                PageState state = newPage(pdf, document, 1);
                content = state.content();
                y = state.y();
                int pageNumber = 1;
                for (OrderItem item : document.order().getItems()) {
                    if (y < 105) {
                        content.close();
                        PageState next = newPage(pdf, document, ++pageNumber);
                        content = next.content();
                        y = next.y();
                    }
                    text(content, item.getServiceName(), LEFT, y, REGULAR, 9);
                    text(content, item.getBillableQuantity().stripTrailingZeros().toPlainString() + " " + item.getPricingUnit(), 300, y, REGULAR, 9);
                    text(content, String.valueOf(item.getPieceCount()), 390, y, REGULAR, 9);
                    text(content, money(item.getUnitRate()), 450, y, REGULAR, 9);
                    text(content, money(item.getLineTotal()), 505, y, REGULAR, 9);
                    y -= 17;
                }
                if (y < 215) {
                    content.close();
                    PageState next = newPage(pdf, document, ++pageNumber);
                    content = next.content();
                    y = next.y();
                }
                y = drawTotals(content, document, y);
                drawFooter(content, document, pageNumber, y);
            } finally {
                if (content != null) content.close();
            }
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not create the invoice PDF", error);
        }
    }

    private PageState newPage(PDDocument pdf, DocumentService.DocumentBundle document,
            int pageNumber) throws IOException {
        PDPage page = new PDPage(PAGE);
        pdf.addPage(page);
        PDPageContentStream content = new PDPageContentStream(pdf, page);
        text(content, document.business().name(), LEFT, TOP, BOLD, 18);
        text(content, document.business().address(), LEFT, TOP - 20, REGULAR, 9);
        text(content, businessPhone(document.business().phone()), LEFT, TOP - 34, REGULAR, 9);
        text(content, "INVOICE", 430, TOP, BOLD, 16);
        text(content, document.order().getInvoiceNumber(), 430, TOP - 20, REGULAR, 9);
        text(content, document.order().getOrderNumber(), 430, TOP - 34, REGULAR, 9);
        float y = TOP - 82;
        text(content, "Bill to: " + document.order().getCustomer().getName(), LEFT, y, BOLD, 10);
        text(content, document.order().getCustomer().getPhone(), LEFT, y - 15, REGULAR, 9);
        text(content, "Placed: " + date(document.order().getPlacedAt()), 300, y, REGULAR, 9);
        text(content, "Delivery: " + date(document.order().getDeliveryAt()), 300, y - 15, REGULAR, 9);
        text(content, "SERVICE", LEFT, y - 52, BOLD, 8);
        text(content, "QTY", 300, y - 52, BOLD, 8);
        text(content, "PCS", 390, y - 52, BOLD, 8);
        text(content, "RATE", 450, y - 52, BOLD, 8);
        text(content, "AMOUNT", 505, y - 52, BOLD, 8);
        text(content, "Page " + pageNumber, 500, 28, REGULAR, 8);
        return new PageState(content, y - 70);
    }

    private float drawTotals(PDPageContentStream content, DocumentService.DocumentBundle document, float y) throws IOException {
        text(content, "Subtotal", 350, y, REGULAR, 9);
        text(content, money(document.order().getSubtotal()), 505, y, REGULAR, 9);
        text(content, "Discount", 350, y - 15, REGULAR, 9);
        text(content, money(document.order().getDiscount()), 505, y - 15, REGULAR, 9);
        text(content, "Tax", 350, y - 30, REGULAR, 9);
        text(content, money(document.order().getTax()), 505, y - 30, REGULAR, 9);
        text(content, "Round-off", 350, y - 45, REGULAR, 9);
        text(content, money(document.order().getRoundOff()), 505, y - 45, REGULAR, 9);
        text(content, "Total", 350, y - 65, BOLD, 11);
        text(content, money(document.order().getTotal()), 505, y - 65, BOLD, 11);
        text(content, "Paid", 350, y - 85, REGULAR, 9);
        text(content, money(document.paymentSummary().amountPaid()), 505, y - 85, REGULAR, 9);
        text(content, "Outstanding", 350, y - 100, BOLD, 10);
        text(content, money(document.paymentSummary().balance()), 505, y - 100, BOLD, 10);
        text(content, "Payment status: " + document.order().getPaymentStatus(), LEFT, y - 130, REGULAR, 9);
        int paymentLine = 0;
        for (var payment : document.paymentSummary().payments()) {
            text(content, "Payment " + payment.getPaymentNumber() + " · " + payment.getMode() + " · "
                    + money(payment.getAmount()) + (payment.getTransactionReference() == null ? "" : " · " + payment.getTransactionReference()),
                    LEFT, y - 148 - paymentLine++ * 13, REGULAR, 8);
        }
        text(content, "Garment tags generated: " + document.tags().size(), LEFT, y - 148 - paymentLine++ * 13, REGULAR, 8);
        return y - 165 - paymentLine * 13;
    }

    private void drawFooter(PDPageContentStream content, DocumentService.DocumentBundle document, int pageNumber, float y)
            throws IOException {
        String upi = document.paymentSummary().balance().signum() > 0 && !upiId.isBlank()
            ? "UPI payment: " + upiId + " (" + upiPayeeName + ") · verify the payee before paying."
            : document.paymentSummary().balance().signum() > 0
            ? "UPI payment information is not configured."
                : "Payment complete.";
        text(content, upi, LEFT, Math.max(45, y), REGULAR, 8);
        text(content, "Thank you for choosing " + document.business().name() + ".", LEFT, Math.max(30, y - 13), REGULAR, 8);
    }

    private String date(java.time.Instant instant) {
        return instant == null ? "Not set" : DATE_TIME.format(instant.atZone(businessZone));
    }

    private static void text(PDPageContentStream content, String value, float x, float y, PDFont font, float size)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(value == null ? "" : value.replaceAll("[\\r\\n]", " "));
        content.endText();
    }

    private static String money(BigDecimal value) {
        return "INR " + (value == null ? BigDecimal.ZERO : value.setScale(2).toPlainString());
    }

    private static String businessPhone(String value) {
        return value == null || value.isBlank() ? "Phone not configured" : value;
    }

    private record PageState(PDPageContentStream content, float y) {}
}