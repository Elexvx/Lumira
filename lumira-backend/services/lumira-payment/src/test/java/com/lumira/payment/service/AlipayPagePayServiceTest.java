package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlipayPagePayServiceTest {

    @Test
    void buildsVerifiableSandboxPagePayRequest() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setEnvironment("SANDBOX");
        settings.setAppId("sandbox-app-id");
        settings.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        settings.setApiBaseUrl("https://openapi.alipay.com");

        String url = new AlipayPagePayService(new ObjectMapper()).buildPagePayUrl(
                settings,
                "SBX-1001",
                "Lumira sandbox receipt verification",
                1L,
                "https://bm.aiadc.org.cn/api/v2/payment/webhooks/alipay",
                "https://bm.aiadc.org.cn/settings/payment?tab=sandbox-orders"
        );

        URI uri = URI.create(url);
        assertThat(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath())
                .isEqualTo(AlipayPagePayService.SANDBOX_GATEWAY);
        Map<String, String> parameters = parseQuery(uri.getRawQuery());
        assertThat(parameters)
                .containsEntry("method", "alipay.trade.page.pay")
                .containsEntry("sign_type", "RSA2")
                .containsEntry("app_id", "sandbox-app-id")
                .containsEntry("notify_url", "https://bm.aiadc.org.cn/api/v2/payment/webhooks/alipay");
        assertThat(parameters.get("biz_content"))
                .contains("\"out_trade_no\":\"SBX-1001\"")
                .contains("\"total_amount\":\"0.01\"")
                .contains("\"product_code\":\"FAST_INSTANT_TRADE_PAY\"");

        String signature = parameters.remove("sign");
        String content = new TreeMap<>(parameters).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(content.getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getDecoder().decode(signature))).isTrue();
    }

    @Test
    void rejectsMalformedApplicationPrivateKey() {
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setEnvironment("SANDBOX");
        settings.setAppId("sandbox-app-id");
        settings.setPrivateKey("not-a-private-key");

        assertThatThrownBy(() -> new AlipayPagePayService(new ObjectMapper()).buildPagePayUrl(
                settings, "SBX-1001", "subject", 1L, null, null
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("PKCS#8");
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            values.put(
                    URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8)
            );
        }
        return values;
    }
}
