package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.IPaymentGatewayPort;
import com.eventsystem.application.order.PaymentResult;
import com.eventsystem.application.order.RefundResult;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.infrastructure.external.wsep.common.WsepAction;
import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepEmptyResponseException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import com.eventsystem.infrastructure.external.wsep.common.WsepResponseParser;
import com.eventsystem.infrastructure.external.wsep.common.WsepPaymentDetails;


import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
@Primary
public class PaymentGatewayHttpAdapter implements IPaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayHttpAdapter.class);

    private static final String UNEXPECTED_RESPONSE_MESSAGE =
            "We received an unexpected response from the payment provider, so your payment could not be confirmed. Please try again.";

    private final WsepHttpClient client;

    public PaymentGatewayHttpAdapter(WsepHttpClient client) {
        this.client = client;
    }

    @Override
    public PaymentResult charge(String orderId, Money amount, BuyerReference buyer, String paymentDetailsToken) {
        log.info("Calling WSEP payment pay action for orderId={}, amount={}, currency={}",
            orderId, amount.amount(), amount.currency());
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

            // WSEP pay has three outcomes: a numeric transaction id (approved), "-1" (declined),
            // or anything else (an unexpected response that must NOT count as a payment).
            if (WsepResponseParser.isFailure(response)) {
                log.warn("WSEP payment declined for orderId={}", orderId);
                return PaymentResult.failed(
                        "Your card was declined. Please check the card number, expiry date and CVV, then try again.");
            }

            if (!WsepResponseParser.isPayTransactionId(response)) {
                log.error("Unexpected WSEP payment response for orderId={}: {}", orderId, response);
                return PaymentResult.failed(UNEXPECTED_RESPONSE_MESSAGE);
            }

            log.info("WSEP payment succeeded for orderId={}, transactionId={}", orderId, response.trim());
            return PaymentResult.successful(response.trim());

        } catch (WsepEmptyResponseException e) {
            // WSEP answered (HTTP 200) but with an empty body — an unexpected response, not a
            // normal decline and not a connectivity failure. Fail the payment with a clear message.
            log.error("WSEP returned an empty payment response for orderId={}", orderId);
            return PaymentResult.failed(UNEXPECTED_RESPONSE_MESSAGE);

        } catch (WsepCommunicationException e) {
            log.error("WSEP payment communication failure for orderId={}", orderId, e);
            throw e;

        } catch (Exception e) {
            log.warn("WSEP payment request failed before/after call for orderId={}: {}", orderId, e.getMessage());
            return PaymentResult.failed(e.getMessage());
        }
    }

    @Override
    public RefundResult refund(String transactionId, Money amount, String reason) {
        log.info("Calling WSEP payment refund action for transactionId={}", transactionId);
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("action_type", WsepAction.REFUND.actionType());
            params.put("transaction_id", transactionId);

            String response = client.post(params);

            if (WsepResponseParser.isSuccessOne(response)) {
                log.info("WSEP refund succeeded for transactionId={}", transactionId);
                return new RefundResult(true, null);
            }

            log.warn("WSEP refund rejected for transactionId={}, response={}", transactionId, response);
            return new RefundResult(false, "Refund rejected by WSEP. Response: " + response);

        } catch (WsepCommunicationException e) {
            log.error("WSEP refund communication failure for transactionId={}", transactionId, e);
            throw e;

        } catch (Exception e) {
            log.warn("WSEP refund request failed for transactionId={}: {}", transactionId, e.getMessage());
            return new RefundResult(false, e.getMessage());
        }
    }
}