package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.IPaymentGatewayPort;
import com.eventsystem.application.order.PaymentResult;
import com.eventsystem.application.order.RefundResult;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.shared.Money;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Primary
public class PaymentGatewayHttpAdapter implements IPaymentGatewayPort {

    private final WsepHttpClient client;

    public PaymentGatewayHttpAdapter(WsepHttpClient client) {
        this.client = client;
    }

    @Override
    public PaymentResult charge(String orderId, Money amount, BuyerReference buyer, String paymentDetailsToken) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", WsepAction.ACTION_PAY.actionString());
        params.put("order_id", orderId);
        params.put("amount", amount.amount().toPlainString());
        params.put("buyer_id", buyer.memberId());
        params.put("payment_token", paymentDetailsToken);

        String response = client.post(params);

        if (isFailure(response)) {
            return PaymentResult.failed("Payment declined by WSEP");
        }

        return PaymentResult.successful(response);
    }

    @Override
    public RefundResult refund(String transactionId, Money amount, String reason) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", WsepAction.ACTION_REFUND.actionString());
        params.put("transaction_id", transactionId);
        params.put("amount", amount.amount().toPlainString());
        params.put("reason", reason);

        String response = client.post(params);

        if (isFailure(response)) {
            return new RefundResult(false, "Refund rejected by WSEP");
        }

        return new RefundResult(true, null);
    }

    private boolean isFailure(String response) {
        return response == null
                || response.isBlank()
                || response.equalsIgnoreCase("false")
                || response.equals("0")
                || response.equals("-1");
    }
}