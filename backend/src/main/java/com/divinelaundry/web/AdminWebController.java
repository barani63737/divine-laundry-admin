package com.divinelaundry.web;

import com.divinelaundry.domain.*;
import com.divinelaundry.repository.*;
import com.divinelaundry.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.security.Principal;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.divinelaundry.config.WhatsappProviderProperties;

@Controller
public class AdminWebController {
    private final CustomerRepository customers;
    private final LaundryServiceRepository catalog;
    private final LaundryOrderRepository orders;
    private final CustomerWebService customerService;
    private final WebOrderService webOrders;
    private final PaymentService payments;
    private final DocumentService documents;
    private final GarmentTagRepository garmentTags;
    private final InvoicePaymentImageService images;
    private final PdfInvoiceService pdfInvoices;
    private final PaymentReceiptService paymentReceipts;
    private final PdfReceiptService pdfReceipts;
    private final WhatsappService whatsapp;
    private final WhatsappMessageRepository messages;
    private final PaymentRequestRepository paymentRequestRows;
    private final PaymentRequestService paymentRequestService;
        private final PaymentRepository paymentRows;
        private final OrderStatusHistoryRepository statusHistory;
        private final OrderService orderService;
        private static final List<OrderStatus> IN_PROCESS = List.of(OrderStatus.RECEIVED, OrderStatus.WASHING,
            OrderStatus.IRONING, OrderStatus.CLEANED, OrderStatus.REWORK);
        private static final List<OrderStatus> OPEN = List.of(OrderStatus.DRAFT, OrderStatus.RECEIVED, OrderStatus.WASHING,
            OrderStatus.IRONING, OrderStatus.CLEANED, OrderStatus.READY, OrderStatus.REWORK);
        private static final List<PaymentStatus> OUTSTANDING = List.of(PaymentStatus.UNPAID, PaymentStatus.PARTIAL);
    private final ZoneId zone;
    private final String businessName;
    private final boolean upiConfigured;
    private final WhatsappProviderProperties whatsappProperties;

    public AdminWebController(CustomerRepository customers, LaundryServiceRepository catalog,
            LaundryOrderRepository orders, CustomerWebService customerService, WebOrderService webOrders,
            PaymentService payments, DocumentService documents, InvoicePaymentImageService images,
            WhatsappMessageRepository messages, GarmentTagRepository garmentTags, @Value("${app.business-zone}") String zone,
            @Value("${app.business.name}") String businessName, @Value("${app.payment.upi-id:}") String upiId,
            PaymentRepository paymentRows, OrderStatusHistoryRepository statusHistory, OrderService orderService,
            PdfInvoiceService pdfInvoices, PaymentReceiptService paymentReceipts, PdfReceiptService pdfReceipts,
            WhatsappProviderProperties whatsappProperties, WhatsappService whatsapp,
            PaymentRequestRepository paymentRequestRows, PaymentRequestService paymentRequestService) {
        this.customers = customers; this.catalog = catalog; this.orders = orders;
        this.customerService = customerService; this.webOrders = webOrders;
        this.payments = payments; this.documents = documents; this.images = images;
        this.pdfInvoices = pdfInvoices;
        this.paymentReceipts = paymentReceipts; this.pdfReceipts = pdfReceipts;
        this.messages = messages; this.garmentTags = garmentTags; this.zone = ZoneId.of(zone); this.businessName = businessName;
        this.upiConfigured = !upiId.isBlank(); this.paymentRows = paymentRows;
        this.statusHistory = statusHistory; this.orderService = orderService;
        this.whatsappProperties = whatsappProperties;
        this.whatsapp = whatsapp;
        this.paymentRequestRows = paymentRequestRows;
        this.paymentRequestService = paymentRequestService;
    }

    @InitBinder
    void limitBinding(WebDataBinder binder) { binder.setAutoGrowCollectionLimit(50); }

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("businessName", businessName);
        model.addAttribute("businessZone", zone);
        model.addAttribute("upiConfigured", upiConfigured);
        model.addAttribute("whatsappImageConfigured", whatsappProperties.isConfigured());
        model.addAttribute("whatsappDocumentConfigured", whatsappProperties.isDocumentConfigured());
        model.addAttribute("whatsappConfigurationState", whatsappProperties.configurationState());
    }

    @GetMapping("/login")
    String login() { return "login"; }

    @GetMapping("/")
    String dashboard(Model model) {
        model.addAttribute("customerCount", customers.count());
        model.addAttribute("orderCount", orders.count());
        model.addAttribute("recentOrders", orders.findTop50ByOrderByPlacedAtDesc());
        Instant start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        model.addAttribute("dashboard", new DashboardMetrics(
            orders.countSalesOrdersBetween(start, end, List.of(OrderStatus.DRAFT, OrderStatus.CANCELLED)),
            orders.countByWorkStatusIn(IN_PROCESS), orders.countByWorkStatus(OrderStatus.READY),
            orders.countByWorkStatus(OrderStatus.DELIVERED),
            orders.sumUnpaidBilledAmount(OUTSTANDING, List.of(OrderStatus.CANCELLED))
                .subtract(paymentRows.sumForOutstandingOrders(OUTSTANDING, List.of(OrderStatus.CANCELLED)))
                .max(java.math.BigDecimal.ZERO),
            paymentRows.sumBetween(start, end, OrderStatus.CANCELLED),
            orders.countByPickupAtBetweenAndWorkStatusIn(start, end, OPEN),
            orders.countByDeliveryAtBetweenAndWorkStatusIn(start, end, OPEN)));
        model.addAttribute("whatsapp", new WhatsappMetrics(
            messages.countByDeliveryStatus(WhatsappDeliveryStatus.PENDING),
            messages.countByDeliveryStatus(WhatsappDeliveryStatus.FAILED),
            messages.countByDeliveryStatus(WhatsappDeliveryStatus.SENT),
            messages.countByDeliveryStatus(WhatsappDeliveryStatus.DELIVERED)));
        return "dashboard";
    }

        @GetMapping("/orders")
        String orderList(@RequestParam(defaultValue = "") String orderQuery,
            @RequestParam(defaultValue = "") String customerQuery,
            @RequestParam(defaultValue = "") String phoneQuery,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) LocalDate orderFrom,
            @RequestParam(required = false) LocalDate orderTo,
            @RequestParam(required = false) LocalDate pickupFrom,
            @RequestParam(required = false) LocalDate pickupTo,
            @RequestParam(required = false) LocalDate deliveryFrom,
            @RequestParam(required = false) LocalDate deliveryTo,
            @RequestParam(defaultValue = "0") int page, Model model) {
        var result = orders.findOperations(orderQuery.trim(), customerQuery.trim(), phoneQuery.trim(), status,
            paymentStatus, startOf(orderFrom), nextDay(orderTo), startOf(pickupFrom), nextDay(pickupTo),
            startOf(deliveryFrom), nextDay(deliveryTo), PageRequest.of(Math.max(0, page), 25,
                Sort.by(Sort.Direction.DESC, "placedAt")));
        model.addAttribute("ordersPage", result);
        model.addAttribute("orderRows", orderRows(result.getContent()));
        model.addAttribute("orderQuery", orderQuery); model.addAttribute("customerQuery", customerQuery);
        model.addAttribute("phoneQuery", phoneQuery); model.addAttribute("status", status);
        model.addAttribute("paymentStatus", paymentStatus); model.addAttribute("orderFrom", orderFrom);
        model.addAttribute("orderTo", orderTo); model.addAttribute("pickupFrom", pickupFrom);
        model.addAttribute("pickupTo", pickupTo); model.addAttribute("deliveryFrom", deliveryFrom);
        model.addAttribute("deliveryTo", deliveryTo); model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("paymentStatuses", PaymentStatus.values());
        return "orders";
        }

    @GetMapping("/customers")
    String customers(@RequestParam(defaultValue = "") String q, Model model) {
        String phoneQuery;
        try { phoneQuery = customerService.normalizePhone(q); }
        catch (IllegalArgumentException ignored) { phoneQuery = q; }
        var rows = customers.findTop30ByNameContainingIgnoreCaseOrPhoneContainingOrderByNameAsc(q, phoneQuery);
        model.addAttribute("q", q);
        model.addAttribute("customers", customerService.listRows(rows));
        return "customers";
    }

    @GetMapping("/customers/new")
    String newCustomer(Model model) { model.addAttribute("customerForm", new CustomerForm()); return "customer-form"; }

    @GetMapping("/customers/{id}")
    String customerDetail(@PathVariable Long id, Model model) {
        model.addAttribute("profile", customerService.profile(id));
        return "customer-detail";
    }

    @GetMapping("/customers/{id}/edit")
    String editCustomer(@PathVariable Long id, Model model) {
        var customer = customerService.profile(id).customer();
        model.addAttribute("customerForm", customerForm(customer));
        model.addAttribute("editMode", true);
        model.addAttribute("customerId", id);
        return "customer-form";
    }

    @PostMapping("/customers/{id}")
    String updateCustomer(@PathVariable Long id, @Valid @ModelAttribute CustomerForm customerForm,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                customerService.update(id, customerForm);
                redirect.addFlashAttribute("success", "Customer details updated.");
                return "redirect:/customers/{id}";
            } catch (IllegalArgumentException ex) { errors.reject("invalid", ex.getMessage()); }
            catch (DataIntegrityViolationException ex) { errors.reject("duplicate", "This mobile number already exists."); }
        }
        model.addAttribute("editMode", true);
        model.addAttribute("customerId", id);
        return "customer-form";
    }

    @PostMapping("/customers")
    String createCustomer(@Valid @ModelAttribute CustomerForm customerForm, BindingResult errors, Model model) {
        if (errors.hasErrors()) return "customer-form";
        try {
            Customer saved = customerService.create(customerForm);
            return "redirect:/orders/new?customerId=" + saved.getId();
        } catch (IllegalArgumentException ex) { errors.reject("invalid", ex.getMessage()); }
        catch (DataIntegrityViolationException ex) { errors.reject("duplicate", "This mobile number already exists. Open Customers to find it."); }
        return "customer-form";
    }

    @GetMapping("/orders/new")
    String newOrder(@RequestParam(required = false) Long customerId, Model model) {
        var form = new OrderForm();
        form.setCustomerId(customerId);
        form.setDeliveryAt(LocalDateTime.now(zone).plusDays(2).withSecond(0).withNano(0).toString());
        form.getItems().add(new LineForm());
        model.addAttribute("orderForm", form);
        populateOrder(model);
        return "order-form";
    }

    private void populateOrder(Model model) {
        model.addAttribute("customers", customers.findByActiveTrueOrderByNameAsc());
        model.addAttribute("catalog", catalog.findByActiveTrueOrderByCategoryAscNameAsc());
    }

    @PostMapping("/orders")
    String createOrder(@Valid @ModelAttribute OrderForm orderForm, BindingResult errors, Model model, Principal principal) {
        if (!errors.hasErrors()) {
            try { return "redirect:/orders/" + webOrders.create(orderForm, principal.getName()); }
            catch (IllegalArgumentException | IllegalStateException ex) { errors.reject("invalid", ex.getMessage()); }
            catch (DataIntegrityViolationException ex) { errors.reject("conflict", "The submission conflicts with an existing record. Check the dashboard before retrying."); }
        }
        populateOrder(model);
        return "order-form";
    }

    @GetMapping("/orders/{number}")
    String detail(@PathVariable String number, Model model) {
        model.addAttribute("paymentForm", new PaymentForm());
        populateDetail(number, model);
        return "order-detail";
    }

    @PostMapping("/orders/{number}/payment-request/whatsapp")
    String requestPaymentViaWhatsapp(@PathVariable String number,
            @RequestParam BigDecimal amount, @RequestParam String idempotencyKey,
            Principal principal, RedirectAttributes redirect) {
        try {
            paymentRequestService.createPaymentRequest(number, amount, principal.getName(), idempotencyKey);
            redirect.addFlashAttribute("success", "Payment request created and WhatsApp delivery queued.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/" + number;
    }

    private void populateDetail(String number, Model model) {
        var summary = payments.summary(number);
        model.addAttribute("order", summary.order());
        model.addAttribute("summary", summary);
        model.addAttribute("paymentRequests", paymentRequestRows.findByOrderIdOrderByCreatedAtDesc(summary.order().getId()));
        var tags = garmentTags.findByOrder_IdOrderByOrderItem_IdAscPieceSequenceAsc(summary.order().getId());
        model.addAttribute("tagCount", tags.size());
        model.addAttribute("printedTagCount", tags.stream().filter(tag -> tag.getPrintCount() > 0).count());
        model.addAttribute("reprintCount", tags.stream().mapToInt(tag -> Math.max(0, tag.getPrintCount() - 1)).sum());
        model.addAttribute("physicalTagCount", summary.order().getItems().stream()
            .filter(item -> !item.isNoPrint()).mapToInt(OrderItem::getPieceCount).sum());
        model.addAttribute("tags", tags);
        model.addAttribute("statusHistory", statusHistory.findByOrder_OrderNumberOrderByChangedAtAsc(number));
        model.addAttribute("whatsappMessages", messages.findByOrder_OrderNumberOrderByCreatedAtDesc(number).stream()
            .map(AdminWebController::whatsappMessageView).toList());
        model.addAttribute("nextStatuses", Arrays.stream(OrderStatus.values())
            .filter(next -> OrderStatus.isValidTransition(summary.order().getWorkStatus(), next)).toList());
        model.addAttribute("modes", PaymentMode.values());
        String state = summary.order().getInvoiceNumber() == null ? "No invoice" : messages
                .findByDeduplicationKey("INVOICE_IMAGE:" + summary.order().getInvoiceNumber())
                .map(message -> message.getDeliveryStatus().name()).orElse("NOT_QUEUED");
        model.addAttribute("whatsappState", state);
    }

    @PostMapping("/orders/{number}/whatsapp/{messageId}/retry")
    String retryWhatsapp(@PathVariable String number, @PathVariable Long messageId, RedirectAttributes redirect) {
        try {
            whatsapp.retry(number, messageId);
            redirect.addFlashAttribute("success", "WhatsApp message retry completed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/" + number;
    }

    @PostMapping("/orders/{number}/status")
    String changeStatus(@PathVariable String number, @RequestParam OrderStatus status,
            Principal principal, RedirectAttributes redirect) {
        try {
            orderService.changeStatus(number, status, principal.getName());
            redirect.addFlashAttribute("success", "Order status updated to " + status + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/" + number;
    }

    private Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(zone).toInstant();
    }

    private Instant nextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();
    }

        private List<OrderRow> orderRows(List<LaundryOrder> pageOrders) {
        if (pageOrders.isEmpty()) return List.of();
        var ids = pageOrders.stream().map(LaundryOrder::getId).toList();
        var tagsByOrder = garmentTags.findByOrder_IdInOrderByOrder_IdAscOrderItem_IdAscPieceSequenceAsc(ids)
            .stream().collect(java.util.stream.Collectors.groupingBy(tag -> tag.getOrder().getId()));
        var paidByOrder = paymentRows.findByOrder_IdInOrderByPaidAtDesc(ids).stream()
            .collect(java.util.stream.Collectors.groupingBy(payment -> payment.getOrder().getId(),
                java.util.stream.Collectors.reducing(java.math.BigDecimal.ZERO, Payment::getAmount,
                    java.math.BigDecimal::add)));
        return pageOrders.stream().map(order -> {
            var tags = tagsByOrder.getOrDefault(order.getId(), List.of());
            var paid = paidByOrder.getOrDefault(order.getId(), java.math.BigDecimal.ZERO);
                return new OrderRow(order, paid, order.getTotal().subtract(paid).max(java.math.BigDecimal.ZERO),
                    order.getItems().stream().filter(item -> !item.isNoPrint()).mapToInt(OrderItem::getPieceCount).sum(),
                    tags.size(), tags.stream().filter(tag -> tag.getPrintCount() > 0).count(),
                tags.stream().mapToInt(tag -> Math.max(0, tag.getPrintCount() - 1)).sum());
        }).toList();
    }

    record OrderRow(LaundryOrder order, java.math.BigDecimal paid, java.math.BigDecimal balance,
            int physicalTagCount, int tagCount, long printedTagCount, int reprintCount) {}

    record DashboardMetrics(long todayOrders, long pendingOrders, long readyOrders, long deliveredOrders,
            java.math.BigDecimal outstanding, java.math.BigDecimal todayCollections,
            long pickupDueToday, long deliveryDueToday) {}

    record WhatsappMetrics(long pending, long failed, long sent, long delivered) {}

    record WhatsappMessageView(Long id, String type, String recipientPhone, String status,
            String failureClassification, int attemptCount, Instant createdAt, Instant sentAt,
            Instant claimedAt, String providerMessageId, boolean retryable) {}

    private static WhatsappMessageView whatsappMessageView(WhatsappMessage message) {
        String key = message.getDeduplicationKey();
        String type = key.startsWith("INVOICE_IMAGE:") ? "Invoice Image"
            : key.startsWith("INVOICE_PDF:") ? "Invoice PDF"
            : key.startsWith("RECEIPT_PDF:") ? "Payment Receipt PDF" : "WhatsApp Message";
        return new WhatsappMessageView(message.getId(), type, maskPhone(message.getRecipientPhone()),
            message.getDeliveryStatus().name(), message.getDeliveryStatus() == WhatsappDeliveryStatus.FAILED
                ? message.getLastError() : null, message.getAttemptCount(), message.getCreatedAt(),
            message.getSentAt(), message.getClaimedAt(), message.getProviderMessageId(),
            message.getDeliveryStatus() == WhatsappDeliveryStatus.FAILED);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) return "****";
        return "******" + phone.substring(phone.length() - 4);
    }

    @PostMapping("/orders/{number}/payments")
    String pay(@PathVariable String number, @Valid @ModelAttribute PaymentForm paymentForm,
            BindingResult errors, Model model, Principal principal, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                payments.record(new PaymentService.RecordPaymentCommand(paymentForm.getRequestId(), number,
                        paymentForm.getMode(), paymentForm.getReference(), paymentForm.getAmount(), Instant.now(), principal.getName()));
                redirect.addFlashAttribute("success", "Payment recorded. This is a manual entry, not bank verification.");
                return "redirect:/orders/" + number;
            } catch (IllegalArgumentException | IllegalStateException ex) { errors.reject("invalid", ex.getMessage()); }
            catch (org.springframework.dao.OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                errors.reject("conflict", "This order changed during payment. Refresh and check its payment history before retrying.");
            }
        }
        populateDetail(number, model);
        return "order-detail";
    }

    @GetMapping("/orders/{number}/invoice")
    String invoice(@PathVariable String number, Model model) {
        model.addAttribute("document", documents.document(number));
        return "invoice";
    }

    @GetMapping(value = "/orders/{number}/invoice.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    ResponseEntity<byte[]> image(@PathVariable String number) {
        var bundle = documents.document(number);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(bundle.order().getInvoiceNumber() + ".png").build().toString())
                .body(images.render(bundle));
    }

    @GetMapping(value = "/orders/{number}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @ResponseBody
    ResponseEntity<byte[]> pdf(@PathVariable String number) {
        var bundle = documents.document(number);
        byte[] content = pdfInvoices.render(bundle);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentLength(content.length)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(bundle.order().getInvoiceNumber() + ".pdf").build().toString())
            .body(content);
    }

            @GetMapping("/orders/{number}/payments/{paymentNumber}/receipt")
            String paymentReceipt(@PathVariable String number, @PathVariable String paymentNumber, Model model) {
            model.addAttribute("receipt", paymentReceipts.document(number, paymentNumber));
            return "payment-receipt";
            }

        @PostMapping("/orders/{number}/payments/{paymentNumber}/whatsapp")
        String sendReceiptWhatsapp(@PathVariable String number, @PathVariable String paymentNumber,
                RedirectAttributes redirect) {
            try {
                whatsapp.sendPaymentUpdate(number, paymentNumber);
                redirect.addFlashAttribute("success", "Receipt WhatsApp send requested.");
            } catch (IllegalArgumentException | IllegalStateException ex) {
                redirect.addFlashAttribute("error", ex.getMessage());
            }
            return "redirect:/orders/" + number;
        }

        @PostMapping("/orders/{number}/invoice-pdf/whatsapp")
        String sendInvoicePdfWhatsapp(@PathVariable String number, RedirectAttributes redirect) {
            try {
                whatsapp.queueInvoicePdf(number);
                redirect.addFlashAttribute("success", "Invoice PDF WhatsApp send requested.");
            } catch (IllegalArgumentException | IllegalStateException ex) {
                redirect.addFlashAttribute("error", ex.getMessage());
            }
            return "redirect:/orders/" + number;
        }

            @GetMapping(value = "/orders/{number}/payments/{paymentNumber}/receipt.pdf",
                produces = MediaType.APPLICATION_PDF_VALUE)
            @ResponseBody
            ResponseEntity<byte[]> paymentReceiptPdf(@PathVariable String number, @PathVariable String paymentNumber) {
            var receipt = paymentReceipts.document(number, paymentNumber);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(receipt.receiptNumber() + ".pdf").build().toString())
                .body(pdfReceipts.render(receipt));
            }

    @GetMapping("/services")
    String services(Model model) { model.addAttribute("catalog", catalog.findAllByOrderByCategoryAscNameAsc()); return "services"; }

        @GetMapping("/reports")
        String reports(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, Model model) {
        LocalDate today = LocalDate.now(zone);
        LocalDate reportFrom = from == null ? today.withDayOfMonth(1) : from;
        LocalDate reportTo = to == null ? today : to;
        if (reportTo.isBefore(reportFrom)) throw new IllegalArgumentException("Report end date must be on or after start date");
        Instant start = reportFrom.atStartOfDay(zone).toInstant();
        Instant end = reportTo.plusDays(1).atStartOfDay(zone).toInstant();
        List<OrderStatus> excluded = List.of(OrderStatus.DRAFT, OrderStatus.CANCELLED);
        BigDecimal sales = orders.sumSalesBetween(start, end, excluded);
        long orderCount = orders.countSalesOrdersBetween(start, end, excluded);
        BigDecimal averageBill = orderCount == 0 ? BigDecimal.ZERO
            : sales.divide(BigDecimal.valueOf(orderCount), 2, java.math.RoundingMode.HALF_UP);
        model.addAttribute("report", new SalesReportView(reportFrom, reportTo, sales, orderCount, averageBill,
            orders.serviceSalesBetween(start, end, excluded)));
        return "reports";
        }

    private CustomerForm customerForm(Customer customer) {
        var form = new CustomerForm();
        form.setName(customer.getName());
        form.setPhone(customer.getPhone());
        form.setAlternatePhone(customer.getAlternatePhone());
        form.setEmail(customer.getEmail());
        form.setAddressLine(customer.getAddressLine());
        form.setArea(customer.getArea());
        form.setNotes(customer.getNotes());
        form.setActive(customer.isActive());
        return form;
    }

    record SalesReportView(LocalDate from, LocalDate to, BigDecimal grossSales, long orderCount,
            BigDecimal averageBill, List<ServiceSalesRow> serviceSales) {}
}
