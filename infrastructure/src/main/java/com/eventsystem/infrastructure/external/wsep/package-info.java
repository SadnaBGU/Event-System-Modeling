/**
 * Infrastructure adapters for the WSEP external systems.
 *
 * <p>This package contains the concrete WSEP-facing adapters used by the
 * application layer during checkout and initialization-related external-service
 * checks. These classes translate application-level ports and operations into
 * real HTTP POST calls to the WSEP API.</p>
 *
 * <p>Main classes:</p>
 * <ul>
 *   <li>
 *     {@code PaymentGatewayHttpAdapter} -
 *     implements the application payment port by calling WSEP payment actions.
 *     It maps charge requests to {@code action_type=pay} and refund requests to
 *     {@code action_type=refund}.
 *   </li>
 *   <li>
 *     {@code TicketIssuanceHttpAdapter} -
 *     implements the application ticket-issuance port by calling WSEP ticket
 *     actions. It maps ticket generation requests to
 *     {@code action_type=issue_ticket}.
 *   </li>
 *   <li>
 *     {@code WsepAvailabilityClient} -
 *     checks whether the external WSEP service is reachable by calling
 *     {@code action_type=handshake}.
 *   </li>
 * </ul>
 *
 * <p>WSEP-specific protocol details such as action names, form parameter names,
 * response parsing, and communication exceptions must remain inside the
 * infrastructure layer and must not leak into the domain layer.</p>
 *
 * <p>Shared protocol helpers are located in
 * {@code com.eventsystem.infrastructure.external.wsep.common}.</p>
 */
package com.eventsystem.infrastructure.external.wsep;