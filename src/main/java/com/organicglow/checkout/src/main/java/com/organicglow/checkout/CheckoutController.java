package com.organicglow.checkout;

import com.squareup.square.SquareClient;
import com.squareup.square.api.CheckoutApi;
import com.squareup.square.exceptions.ApiException;
import com.squareup.square.models.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

  private final String TOKEN  = System.getenv("SQUARE_ACCESS_TOKEN");
  private final String LOC    = System.getenv("SQUARE_LOCATION_ID");
  private final String TY_URL = System.getenv("THANK_YOU_URL") != null
      ? System.getenv("THANK_YOU_URL")
      : "https://organicglowskinstudio.com/thank-you";

  private final SquareClient client = new SquareClient.Builder()
      .accessToken(TOKEN)
      .environment(TOKEN != null && TOKEN.startsWith("EAAA")
          ? com.squareup.square.Environment.SANDBOX
          : com.squareup.square.Environment.PRODUCTION)
      .build();

  // Meta calls: /checkout/redirect?products=VARIATION_ID:QTY,...&coupon=CODE
  @GetMapping("/redirect")
  public ResponseEntity<Void> redirect(
      @RequestParam String products,
      @RequestParam(required = false) String coupon,
      @RequestParam Map<String,String> all
  ) throws ApiException, IOException {

    if (!StringUtils.hasText(TOKEN) || !StringUtils.hasText(LOC)) {
      return ResponseEntity.status(500).header("X-Error","Missing Square credentials").build();
    }

    // Parse items
    List<OrderLineItem> items = new ArrayList<>();
    for (String entry : products.split(",")) {
      if (!StringUtils.hasText(entry)) continue;
      String[] parts = entry.split(":");
      String variationId = parts[0].trim();
      int qty = (parts.length > 1 && parts[1].trim().matches("\\d+")) ? Integer.parseInt(parts[1].trim()) : 1;

      items.add(new OrderLineItem(String.valueOf(qty))
          .toBuilder()
          .catalogObjectId(variationId) // Square Variation ID
          .itemType("ITEM")
          .build());
    }
    if (items.isEmpty()) return ResponseEntity.badRequest().build();

    // Build order
    Order.Builder orderB = new Order.Builder(LOC).lineItems(items);

    // Optional coupon → simple % off (edit these names/percents if you want)
    if (StringUtils.hasText(coupon)) {
      String pct = mapCoupon(coupon);
      if (pct != null) {
        orderB.discounts(List.of(new OrderLineItemDiscount.Builder()
            .name(coupon.toUpperCase())
            .type("FIXED_PERCENTAGE")
            .percentage(pct)   // e.g. "15"
            .scope("ORDER")
            .build()));
      }
    }
    Order order = orderB.build();

    // Preserve UTMs on thank-you page
    String ty = buildTyUrl(TY_URL, all);

    // Create payment link
    CheckoutApi api = client.getCheckoutApi();
    CreatePaymentLinkResponse resp = api.createPaymentLink(new CreatePaymentLinkRequest.Builder()
        .idempotencyKey(UUID.randomUUID().toString())
        .order(order)
        .redirectUrl(ty)
        .build());

    String url = resp.getPaymentLink() != null ? resp.getPaymentLink().getUrl() : null;
    if (!StringUtils.hasText(url)) return ResponseEntity.status(502).build();

    // Go to Square checkout
    return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
  }

  private String mapCoupon(String c) {
    if (!StringUtils.hasText(c)) return null;
    Map<String,String> map = new HashMap<>();
    map.put("GLOW15","15");
    map.put("WELCOME10","10");
    return map.get(c.trim().toUpperCase());
  }

  private String buildTyUrl(String base, Map<String,String> p) {
    List<String> keep = List.of("utm_source","utm_medium","utm_campaign","utm_content","utm_term");
    StringBuilder sb = new StringBuilder(base);
    boolean first = !base.contains("?");
    for (String k : keep) {
      String v = p.get(k);
      if (StringUtils.hasText(v)) {
        sb.append(first ? "?" : "&"); first = false;
        sb.append(java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8));
        sb.append("=")
          .append(java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8));
      }
    }
    return sb.toString();
  }
}
