// DTOs shared with the WAF REST API. Single source of truth for request/response shapes.
// Update jointly with WAF team when contracts change.

// ---------- Auth & Members ----------
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

// ---------- Catalog ----------

// Paginated envelope returned by listing endpoints.
export interface Page<T> {
  currentPage: number;
  hasNext: boolean;
  totalElements: number;
  totalPages: number;
  items: T[];
}

// Backend EventCatalogController returns the same shape for summary and detail.
export interface EventDto {
  eventId: string;
  eventName: string;
  artist?: string;
  dates: string[]; // ISO LocalDateTime[] (no zone)
  category?: string;
  location?: string;
  description?: string;
  status: string; // backend EventStatus enum name
  salesMethod?: string;
  companyId: string;
  zones: EventZoneDto[];
  venueMap?: VenueMapElementDto[];
  priceSummary?: PriceSummary;
}

export interface EventZoneDto {
  zoneId: string;
  zoneName: string;
  zoneType: 'SEATED' | 'STANDING';
  price: number;
  currency: string;
  totalCapacity: number;
  availableCount: number;
}

export interface PriceSummary {
  minPrice: number;
  maxPrice: number;
  currency: string;
}

export interface VenueMapElementDto {
  elementType: string;
  label?: string;
  positionX?: number;
  positionY?: number;
  linkedZoneId?: string;
}

// ---------- Orders ----------
// Matches backend ActiveOrderDTO.
export type OrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'COMPLETED'
  | 'FAILED'
  | string;

export interface MoneyDto {
  amount: number;
  currency: string;
}

export interface OrderItemDto {
  zoneId: string;
  seatId: string;
  quantity: number;
  unitPrice: MoneyDto;
}

export interface BuyerRefDto {
  buyerType: 'GUEST' | 'MEMBER';
  sessionId?: string | null;
  memberId?: string | null;
}

export interface OrderDto {
  orderId: string;
  buyerRef: BuyerRefDto;
  eventId: string;
  items: OrderItemDto[];
  reservationExpiry: string;
  status: OrderStatus;
  version: number;
}

export interface AddItemRequest {
  zoneId: string;
  seatId?: string;
  quantity?: number;
}

export interface RemoveItemRequest {
  zoneId: string;
  seatId?: string;
  quantity?: number;
}

export interface ApplyDiscountRequest {
  discountCode: string;
}

export interface OrderPricingPreviewDto {
  subtotal: number;
  discount: number;
  total: number;
  currency: string;
}


// CheckoutSagaController expects orderId in the body.
export interface CheckoutRequest {
  orderId: string;
  paymentToken: string;
  discountCode?: string;
}

// ---------- Queue ----------
export interface QueueStatusDto {
  isAdmitted: boolean;
  position?: number;
}

// ---------- Purchase history ----------
// Backend list-receipts items: { recordId, purchaseDate, eventName, totalAmount, currency }
export interface PurchaseRecordSummaryDto {
  recordId: string;
  purchaseDate: string;
  eventName: string;
  totalAmount: number;
  currency: string;
}

// Backend receipt-detail: { recordId, purchaseDate, eventName, totalAmount, currency, paymentStatus, tickets[] }
export interface PurchaseRecordDetailDto extends PurchaseRecordSummaryDto {
  paymentStatus: 'COMPLETED' | 'PENDING' | string;
  tickets: PurchasedTicketDto[];
}

export interface PurchasedTicketDto {
  zoneId: string; // backend currently emits zoneName here under the key "zoneId"
  seatId: string;
  price: number;
}

// ---------- Companies ----------
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

// Backend CompanySalesReportDTO (full structure depends on service; we expose the rows the UI uses).
export interface SalesReportRow {
  eventId: string;
  eventName: string;
  ticketsSold: number;
  grossRevenue: number;
}

// ---------- Roles ----------
export type RoleType = 'OWNER' | 'MANAGER';

// Must match backend domain.company.Permission enum values exactly.
export const ALL_PERMISSIONS = [
  'EVENT_INVENTORY_MANAGEMENT',
  'VENUE_CONFIGURATION',
  'MODIFY_POLICIES',
  'VIEW_PURCHASE_HISTORY',
  'GENERATE_SALES_REPORT',
] as const;
export type Permission = typeof ALL_PERMISSIONS[number];

export interface CompanyRoleDto {
  memberId: string;
  username?: string;
  roleType: RoleType;
  permissions: Permission[];
}

export interface AppointRoleRequest {
  targetUsername: string;
  roleType: RoleType;
  permissionsList: Permission[];
}

export interface AppointmentManagerNodeDto {
  memberId: string;
  memberUsername: string;
  roleType: 'MANAGER';
  appointerId: string;
  appointerUsername: string;
  permissions: Permission[];
  managers: AppointmentManagerNodeDto[];
}

export interface AppointmentOwnerNodeDto {
  memberId: string;
  memberUsername: string;
  roleType: 'OWNER';
  appointerId: string | null;
  appointerUsername: string | null;
  owners: AppointmentOwnerNodeDto[];
  managers: AppointmentManagerNodeDto[];
}

export interface CompanyAppointmentTreeDto {
  companyId: string;
  companyName: string;
  root: AppointmentOwnerNodeDto;
}

// ---------- Event creation (company side) ----------
// Matches backend CompanyController.CreateEventRequest.
export interface CreateEventRequest {
  eventName: string;
  dates: string[]; // ISO LocalDateTime[] (no zone, e.g. "2026-08-01T20:00:00")
  category?: string;
  location?: string;
  description?: string;
}

// ---------- Lottery ----------
export interface LotteryRegistrationRequest {
  zoneId?: string;
}

// ---------- Admin ----------
// Backend SuspensionDto in application layer: { memberId, username, suspendedAt, duration, endsAt }
// where duration is "PERMANENT" or an ISO-8601 duration like "PT24H" / "P1D".
export interface SuspensionDto {
  memberId: string;
  username?: string;
  suspendedAt: string;
  duration: string;
  endsAt: string | null;
}

// AdminController accepts { durationDays?: number, reason?: string }; days==0 / null => permanent.
export interface SuspendRequest {
  durationDays: number | null;
  reason?: string;
}

// Backend AdminStreamController.PurchaseHistoryPageResponse wraps PurchaseRecordDTO[] in {items, page, size, totalElements, totalPages}.
export interface GlobalHistoryRow {
  recordId: string;
  buyerId?: string;
  buyerDisplayName?: string;
  eventSnapshot?: { eventName?: string };
  purchaseTimestamp: string;
  totalPaid: MoneyDto;
}

export interface GlobalHistoryPage {
  items: GlobalHistoryRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------- Notifications ----------
export interface NotificationDto {
  id: string;
  type:
    | 'EVENT_CANCELLED'
    | 'QUEUE_ADMITTED'
    | 'QUEUE_TURN_ARRIVED'
    | 'ORDER_CONFIRMED'
    | 'LOTTERY_WON'
    | 'LOTTERY_LOST'
    | 'ACCOUNT_SUSPENDED'
    | 'GENERIC';
  message: string;
  createdAt: string;
  meta?: Record<string, unknown>;
}

// ---------- Error envelope ----------
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  errorType: string;
  message: string;
  path: string;
}
