// DTOs shared with the WAF REST API. Single source of truth for request/response shapes.


//Auth & Members 
export interface LoginRequest {
  username: string;
  plaintextPassword: string;
}

export interface LoginResponse {
  token: string;
  memberId: string;
  expiresAt: string;
}

export interface RegisterRequest {
  username: string;
  plaintextPassword: string;
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string;
}

export interface MemberDto {
  memberId: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED';
}

//Catalog 
export interface EventSummaryDto {
  eventId: string;
  name: string;
  companyName: string;
  dateTime: string;
  venueName: string;
  startingPrice?: number;
  status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED';
}

export interface EventDetailDto extends EventSummaryDto {
  description?: string;
  zones: ZoneSummaryDto[];
}

export interface ZoneSummaryDto {
  zoneId: string;
  name: string;
  type: 'SEATED' | 'STANDING';
  basePrice: number;
  available: number;
  capacity: number;
}

//Orders
export interface OrderDto {
  orderId: string;
  eventId: string;
  expiresAt: string;
  items: OrderItemDto[];
  totalBeforeDiscount: number;
  totalAfterDiscount: number;
  status: 'ACTIVE' | 'CHECKED_OUT' | 'EXPIRED' | 'CANCELLED';
}

export interface OrderItemDto {
  zoneId: string;
  seatId: string;
  seatLabel?: string;
  unitPrice: number;
}

export interface AddItemRequest {
  zoneId: string;
  seatId: string;
}

export interface CheckoutRequest {
  paymentToken: string;
  discountCode?: string;
}

//Queue
export interface QueueStatusDto {
  isAdmitted: boolean;
  position?: number;
}

//Purchase history
export interface PurchaseRecordDto {
  recordId: string;
  eventId: string;
  eventName: string;
  purchasedAt: string;
  totalPaid: number;
  items: PurchasedItemDto[];
}

export interface PurchasedItemDto {
  zoneName: string;
  seatLabel: string;
  unitPrice: number;
}

//Companies 
export interface CompanyDto {
  companyId: string;
  companyName: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  contactDetails?: string;
}

export interface CreateCompanyRequest {
  companyName: string;
  contactDetails?: string;
}

export interface CompanyStatusUpdate {
  status: 'ACTIVE' | 'SUSPENDED';
}

export interface SalesReportRow {
  eventId: string;
  eventName: string;
  ticketsSold: number;
  grossRevenue: number;
}

//Roles 
export type RoleType = 'OWNER' | 'MANAGER';

export const ALL_PERMISSIONS = [
  'MANAGE_EVENTS',
  'MANAGE_POLICIES',
  'MANAGE_ROLES',
  'VIEW_REPORTS',
] as const;
export type Permission = typeof ALL_PERMISSIONS[number];

export interface CompanyRoleDto {
  memberId: string;
  username?: string;
  roleType: RoleType;
  permissions: Permission[];
}

export interface AppointRoleRequest {
  targetMemberId: string;
  roleType: RoleType;
  permissionsList: Permission[];
}

//Event creation (company side) 
export interface CreateEventRequest {
  name: string;
  description?: string;
  dateTime: string;
  venueName: string;
  zones: { name: string; type: 'SEATED' | 'STANDING'; basePrice: number; capacity: number }[];
}

//Lottery 
export interface LotteryRegistrationRequest {
  zoneId?: string;
}

//Admin
export interface SuspensionDto {
  memberId: string;
  username: string;
  suspendedAt: string;
  durationMinutes: number | null; // null = permanent
  endsAt: string | null;
  reason?: string;
}

export interface SuspendRequest {
  durationMinutes: number | null; // null = permanent
  reason?: string;
}

export interface GlobalHistoryRow {
  recordId: string;
  buyerUsername: string;
  eventName: string;
  purchasedAt: string;
  totalPaid: number;
}

//Notifications 
export interface NotificationDto {
  id: string;
  type:
    | 'EVENT_CANCELLED'
    | 'QUEUE_ADMITTED'
    | 'ORDER_CONFIRMED'
    | 'LOTTERY_WON'
    | 'LOTTERY_LOST'
    | 'ACCOUNT_SUSPENDED'
    | 'GENERIC';
  message: string;
  createdAt: string;
  meta?: Record<string, unknown>;
}

//Error envelope
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  errorType: string;
  message: string;
  path: string;
}
