package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.IPaymentGatewayPort;
import com.eventsystem.application.order.PaymentResult;
import com.eventsystem.application.order.RefundResult;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.shared.Money;

import com.eventsystem.infrastructure.external.wsep.common.WsepAction;
import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import com.eventsystem.infrastructure.external.wsep.common.WsepResponseParser;
import com.eventsystem.infrastructure.external.wsep.common.WsepPaymentDetails;


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
        try {
            WsepPaymentDetails paymentDetails = WsepPaymentDetails.fromJson(paymentDetailsToken);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("action_type", WsepAction.PAY.actionType());
            params.put("amount", amount.amount().toPlainString());
            params.put("currency", amount.currency());
            params.put("card_number", paymentDetails.cardNumber());
            params.put("month", paymentDetails.month());
            params.put("year", paymentDetails.year());
            params.put("holder", paymentDetails.holder());
            params.put("cvv", paymentDetails.cvv());
            params.put("id", paymentDetails.id());

            String response = client.post(params);

            if (WsepResponseParser.isFailure(response)) {
                return PaymentResult.failed("Payment declined by WSEP");
            }

            return PaymentResult.successful(response.trim());
        } catch (WsepCommunicationException e) {
            throw e;
        } catch (Exception e) {
            return PaymentResult.failed(e.getMessage());
        }
    }

    @Override
    public RefundResult refund(String transactionId, Money amount, String reason) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("action_type", WsepAction.REFUND.actionType());
            params.put("transaction_id", transactionId);

            String response = client.post(params);

            if (!WsepResponseParser.isSuccessOne(response)) {
                return new RefundResult(false, "Refund rejected by WSEP. Response: " + response);
            }

            return new RefundResult(true, null);
        } catch (WsepCommunicationException e) {
            throw e;
        } catch (Exception e) {
            return new RefundResult(false, e.getMessage());
        }
    }
}