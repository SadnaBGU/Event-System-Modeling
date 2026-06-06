import { api } from '../client';
import type {
  AddItemRequest,
  CheckoutRequest,
  OrderDto,
} from '../../types/api';

export const ordersApi = {
  openOrCreate: (eventId: string) =>
    api
      .post<{ orderId: string; expiresAt: string }>('/orders/active', { eventId })
      .then((r) => r.data),

  get: (orderId: string) =>
    api.get<OrderDto>(`/orders/${orderId}`).then((r) => r.data),

  addItem: (orderId: string, body: AddItemRequest) =>
    api.post<OrderDto>(`/orders/${orderId}/items`, body).then((r) => r.data),

  removeItem: (orderId: string, seatId: string) =>
    api.delete<OrderDto>(`/orders/${orderId}/items/${seatId}`).then((r) => r.data),

  checkout: (orderId: string, body: CheckoutRequest) =>
    api.post<{ recordId: string }>(`/orders/${orderId}/checkout`, body).then((r) => r.data),
};
