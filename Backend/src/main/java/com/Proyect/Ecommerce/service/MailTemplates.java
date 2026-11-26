package com.Proyect.Ecommerce.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.Proyect.Ecommerce.model.Address;
import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.OrderItem;
import com.Proyect.Ecommerce.service.MailQueue.Mail;

/**
 * HU-20 · The emails that go through {@link MailQueue}: welcome, password reset, order paid and order shipped.
 * Each one in HTML that email clients show the same (tables, inline styles, the store's colors) and in plain text.
 * Everything that comes from people (names, products, addresses) is escaped in the HTML.
 */
@Component
public class MailTemplates {

    private static final Locale AR = Locale.forLanguageTag("es-AR");
    /** "26 de noviembre de 2026, 14:05": the shop sells in Argentina. */
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", AR)
            .withZone(ZoneId.of("America/Argentina/Buenos_Aires"));
    // Colors and fonts of the store's kit (front-end/src/styles.css), written inline as email clients need.
    private static final String INK = "#14161a";
    private static final String MUTED = "#5a606b";
    private static final String LINE = "#d5d8de";
    private static final String CANVAS = "#f4f5f7";
    private static final String ACCENT = "#2440b8";
    private static final String SANS = "'Segoe UI', Arial, sans-serif";
    private static final String DISPLAY = "Georgia, 'Times New Roman', serif";

    /** Long words (a link's token, a product name without spaces) wrap instead of widening the email on a phone. */
    private static final String WRAP = "word-break:break-word;overflow-wrap:anywhere;";

    private final String publicUrl;

    public MailTemplates(@Value("${app.public-url}") String publicUrl) {
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    /** HU-20 · Once the email is confirmed. */
    public Mail welcome(String to, String name) {
        String text = """
                Hola %s,

                Tu email ya está confirmado y tu cuenta de ShopFlow está lista para comprar.

                Mirá el catálogo: %s

                Cada vez que compres, te vamos a avisar por email cuando acreditemos el pago y cuando despachemos tu pedido.
                """.formatted(name, publicUrl);
        String html = page("Te damos la bienvenida",
                paragraph("Hola " + esc(name) + ",")
                        + paragraph("Tu email ya está confirmado y tu cuenta de ShopFlow está lista para comprar.")
                        + button("Ver el catálogo", publicUrl)
                        + paragraph("Cada vez que compres, te vamos a avisar por email cuando acreditemos el pago y "
                                + "cuando despachemos tu pedido."));
        return new Mail(to, "Te damos la bienvenida a ShopFlow", text, html, null);
    }

    /** HU-09 · The link works once and until {@code expiresAt}; the email isn't sent after that. */
    public Mail passwordReset(String to, String name, String token, Instant expiresAt) {
        String link = publicUrl + "/reset-password?token=" + token;
        String text = """
                Hola %s,

                Para elegir una contraseña nueva, abrí este enlace:
                %s

                Vence en 30 minutos y sirve una sola vez. Si no lo pediste, ignorá este mensaje:
                tu contraseña no cambia.
                """.formatted(name, link);
        String html = page("Elegí una contraseña nueva",
                paragraph("Hola " + esc(name) + ",")
                        + paragraph("Para elegir una contraseña nueva, tocá el botón.")
                        + button("Elegir contraseña", link)
                        + small("Vence en 30 minutos y sirve una sola vez. Si no lo pediste, ignorá este mensaje: "
                                + "tu contraseña no cambia.")
                        + small("Si el botón no funciona, copiá este enlace en el navegador: " + unbroken(link)));
        return new Mail(to, "Restablecé tu contraseña de ShopFlow", text, html, expiresAt);
    }

    /** HU-20 · With the prices paid, kept in the order's items (not today's prices). */
    public Mail orderPaid(Order order) {
        String link = orderLink(order);
        String text = """
                Hola %s,

                ¡Gracias por tu compra! Acreditamos el pago y ya estamos preparando tu pedido.

                Pedido #%d · %s

                %s
                Total: %s

                Lo enviamos a:
                %s

                Seguí tu pedido en %s
                """.formatted(order.getUser().getName(), order.getId(), date(order), itemLines(order),
                money(order.getTotal()), addressLine(order.getAddress()), link);
        String html = page("Pedido #" + order.getId(),
                paragraph("Hola " + esc(order.getUser().getName()) + ",")
                        + paragraph("¡Gracias por tu compra! Acreditamos el pago y ya estamos preparando tu pedido.")
                        + small("Pedido #" + order.getId() + " · " + date(order))
                        + itemsTable(order)
                        + shippingTo(order.getAddress())
                        + button("Ver mi pedido", link));
        return new Mail(order.getUser().getEmail(), "Recibimos el pago de tu pedido #" + order.getId(), text, html, null);
    }

    /** HU-20 · Ready for when an order becomes ENVIADA (HU-18, HU-23). */
    public Mail orderShipped(Order order) {
        String link = orderLink(order);
        StringBuilder items = new StringBuilder();
        StringBuilder rows = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            items.append("- ").append(item.getQuantity()).append(" × ").append(item.getProduct().getName()).append('\n');
            rows.append("<li style=\"margin:0 0 4px;").append(WRAP).append("\">").append(item.getQuantity()).append(" × ")
                    .append(esc(item.getProduct().getName())).append("</li>");
        }
        String text = """
                Hola %s,

                Despachamos tu pedido #%d. Ya está en camino.

                %s
                Lo enviamos a:
                %s

                Seguí tu pedido en %s
                """.formatted(order.getUser().getName(), order.getId(), items, addressLine(order.getAddress()), link);
        String html = page("Tu pedido está en camino",
                paragraph("Hola " + esc(order.getUser().getName()) + ",")
                        + paragraph("Despachamos tu pedido #" + order.getId() + ". Ya está en camino.")
                        + "<ul style=\"margin:0 0 16px;padding-left:20px;\">" + rows + "</ul>"
                        + shippingTo(order.getAddress())
                        + button("Ver mi pedido", link));
        return new Mail(order.getUser().getEmail(), "Tu pedido #" + order.getId() + " está en camino", text, html, null);
    }

    // ── pieces ───────────────────────────────────────────────

    private String orderLink(Order order) {
        return publicUrl + "/account/orders/" + order.getId();
    }

    /** Stored dates are the JVM's local time, which EcommerceApplication fixes to UTC (as in OrderService). */
    private static String date(Order order) {
        return DATE.format(order.getCreatedAt().atZone(ZoneId.systemDefault()));
    }

    private static String itemLines(Order order) {
        StringBuilder lines = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            lines.append("- ").append(item.getProduct().getName()).append(": ").append(item.getQuantity())
                    .append(" × ").append(money(item.getUnitPrice())).append(" = ").append(money(subtotal(item)))
                    .append('\n');
        }
        return lines.toString();
    }

    /**
     * Two columns, so it fits a phone's 360 px: each product with "quantity × unit price" under its name, and its
     * subtotal. Amounts never wrap (white-space:nowrap, on top of money's non-breaking space).
     */
    private static String itemsTable(Order order) {
        String cell = "padding:8px 0;border-bottom:1px solid " + LINE + ";vertical-align:top;";
        String amount = "padding-left:12px;white-space:nowrap;";
        StringBuilder rows = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            rows.append("<tr>")
                    .append("<td style=\"").append(cell).append(WRAP).append("\">").append(esc(item.getProduct().getName()))
                    .append("<br><span style=\"font-size:12px;line-height:16px;color:").append(MUTED)
                    .append(";white-space:nowrap;\">").append(item.getQuantity()).append(" × ")
                    .append(money(item.getUnitPrice())).append("</span></td>")
                    .append("<td align=\"right\" style=\"").append(cell).append(amount).append("\">")
                    .append(money(subtotal(item))).append("</td>")
                    .append("</tr>");
        }
        String head = "padding:0 0 8px;border-bottom:1px solid " + LINE + ";font-size:12px;color:" + MUTED + ";font-weight:600;";
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"border-collapse:collapse;margin:0 0 16px;font-size:14px;\">"
                + "<tr><td style=\"" + head + "\">Producto</td>"
                + "<td align=\"right\" style=\"" + head + "\">Subtotal</td></tr>"
                + rows
                + "<tr><td style=\"padding:12px 0 0;font-weight:600;\">Total</td>"
                + "<td align=\"right\" style=\"padding:12px 0 0;" + amount + "font-size:18px;font-weight:600;\">"
                + money(order.getTotal()) + "</td></tr></table>";
    }

    private static String shippingTo(Address address) {
        return small("Lo enviamos a:") + "<p style=\"margin:0 0 16px;" + WRAP + "\">" + esc(addressLine(address)) + "</p>";
    }

    /** "Lucía Pérez, San Martín 123, Rosario, Santa Fe (CP 2000)". */
    private static String addressLine(Address address) {
        return address.getFirstName() + " " + address.getLastName() + ", " + address.getAddress() + ", "
                + address.getCity().getName() + ", " + address.getCity().getState().getName()
                + " (CP " + address.getZipCode() + ")";
    }

    private static BigDecimal subtotal(OrderItem item) {
        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    /**
     * Pesos as the store writes them (ArsPipe): "$ 48.900", and both cent digits when there are any ("$ 62.500,50").
     * The space doesn't break, so a narrow screen never leaves the "$" on a line of its own.
     */
    static String money(BigDecimal value) {
        boolean whole = value.stripTrailingZeros().scale() <= 0;
        DecimalFormat format = new DecimalFormat(whole ? "#,##0" : "#,##0.00", DecimalFormatSymbols.getInstance(AR));
        return "$\u00a0" + format.format(value);
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    /** A link written out: a token has nowhere to break, so it may break anywhere. */
    private static String unbroken(String link) {
        return "<span style=\"word-break:break-all;overflow-wrap:anywhere;\">" + esc(link) + "</span>";
    }

    private static String paragraph(String html) {
        return "<p style=\"margin:0 0 16px;\">" + html + "</p>";
    }

    private static String small(String html) {
        return "<p style=\"margin:0 0 8px;font-size:14px;line-height:20px;color:" + MUTED + ";\">" + html + "</p>";
    }

    private static String button(String label, String href) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 24px;\"><tr>"
                + "<td style=\"background:" + ACCENT + ";border-radius:6px;\">"
                + "<a href=\"" + esc(href) + "\" style=\"display:inline-block;padding:12px 20px;font-family:" + SANS
                + ";font-size:16px;font-weight:600;color:#ffffff;text-decoration:none;\">" + label + "</a>"
                + "</td></tr></table>";
    }

    /** The frame every email shares: the store's name, the content and a footer. */
    private static String page(String title, String content) {
        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + esc(title) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:" + CANVAS + ";\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:"
                + CANVAS + ";\"><tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;"
                + "max-width:600px;background:#ffffff;border:1px solid " + LINE + ";border-radius:8px;\">"
                + "<tr><td style=\"padding:20px 32px;border-bottom:1px solid " + LINE + ";font-family:" + DISPLAY
                + ";font-size:24px;line-height:32px;color:" + INK + ";\">ShopFlow</td></tr>"
                + "<tr><td style=\"padding:32px;font-family:" + SANS + ";font-size:16px;line-height:24px;color:" + INK + ";\">"
                + "<h1 style=\"margin:0 0 24px;font-family:" + DISPLAY + ";font-size:28px;line-height:34px;font-weight:normal;\">"
                + esc(title) + "</h1>"
                + content
                + "</td></tr>"
                + "<tr><td style=\"padding:16px 32px;border-top:1px solid " + LINE + ";font-family:" + SANS
                + ";font-size:12px;line-height:16px;color:" + MUTED + ";\">"
                + "Te escribimos por tu cuenta en ShopFlow. Este email se envía automáticamente: no lo respondas."
                + "</td></tr></table></td></tr></table></body></html>";
    }
}
