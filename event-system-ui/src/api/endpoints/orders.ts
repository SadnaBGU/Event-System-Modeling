import { api } from '../client';
import type {
  AddItemRequest,
  ApplyDiscountRequest,
  RemoveItemRequest,
  CheckoutRequest,
  OrderDto,
  OrderPricingPreviewDto,
} from '../../types/api';

export const ordersApi = {
  // Backend POST /orders/active takes the full BuyerReference; memberId is the authenticated user's id.
  openOrCreate: (eventId: string, memberId: string) =>
    api
      .post<OrderDto>('/orders/active', { eventId, buyerType: 'MEMBER', memberId })
      .then((r) => r.data),

  get: (orderId: string) =>
    api.get<OrderDto>(`/orders/${orderId}`).then((r) => r.data),

  // Reserve seat — backend returns 202 with empty body; caller should refetch the order.
  addItem: (orderId: string, body: AddItemRequest) =>
    api.post<void>(`/orders/${orderId}/items`, body).then(() => undefined),

  // Release seat — backend takes the body via DELETE.
  removeItem: (orderId: string, body: RemoveItemRequest) =>
    api.delete<void>(`/orders/${orderId}/items`, { data: body }).then(() => undefined),

  applyDiscount: (orderId: string, body: ApplyDiscountRequest) =>
    api.post<OrderPricingPreviewDto>(`/orders/${orderId}/discount`, body).then((r) => r.data),

  // Checkout lives on its own controller. Returns 202 with empty body; status is polled separately.
  checkout: (body: CheckoutRequest) =>
    api.post<void>('/checkout', body).then(() => undefined),

  checkoutStatus: (orderId: string) =>
    api.get<{ orderId: string; status: string }>(`/checkout/${orderId}/status`).then((r) => r.data),
};
