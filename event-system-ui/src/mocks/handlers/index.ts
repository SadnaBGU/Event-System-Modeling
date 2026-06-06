import { http, HttpResponse, delay } from 'msw';
import type {
  AddItemRequest,
  AppointRoleRequest,
  CheckoutRequest,
  CompanyDto,
  CompanyRoleDto,
  CompanyStatusUpdate,
  CreateCompanyRequest,
  CreateEventRequest,
  EventDetailDto,
  EventSummaryDto,
  LoginRequest,
  LoginResponse,
  MemberDto,
  OrderDto,
  OrderItemDto,
  PurchaseRecordDto,
  PurchasedItemDto,
  QueueStatusDto,
  RegisterRequest,
  SalesReportRow,
  SuspendRequest,
  SuspensionDto,
  GlobalHistoryRow,
} from '../../types/api';
import type { PolicyBundle } from '../../types/policies';

interface StoredMember extends MemberDto {
  password: string;
}

// Tiny in-memory state. Reset on page reload.
const members = new Map<string, StoredMember>();
const usernameIndex = new Map<string, string>(); // username -> memberId
const tokens = new Map<string, { memberId: string; expiresAt: string }>();
// orderId -> order; one active order per buyer+event for V1 mock purposes
const orders = new Map<string, OrderDto>();
// queue admission: eventId+memberId -> admitted
const admissions = new Set<string>();
// purchase history: memberId -> records
const history = new Map<string, PurchaseRecordDto[]>();
// lottery registrations: eventId -> Set(memberId)
const lotteryRegs = new Map<string, Set<string>>();
// companies
const companies = new Map<string, CompanyDto>();
// companyId -> roles
const companyRoles = new Map<string, CompanyRoleDto[]>();
// policies — separate maps per scope
const companyPolicies = new Map<string, PolicyBundle>();
const eventPolicies = new Map<string, PolicyBundle>();
// memberId -> set of companyIds they manage/own
const memberCompanies = new Map<string, Set<string>>();
// Active suspensions keyed by memberId.
const suspensions = new Map<string, SuspensionDto>();

// Seed an admin and a regular member so devs can sign in immediately.
function seed() {
  const admin: StoredMember = {
    memberId: 'MEM-ADMIN',
    username: 'admin',
    password: 'admin1234',
    firstName: 'Site',
    lastName: 'Admin',
    email: 'admin@example.com',
    dateOfBirth: '1990-01-01',
    status: 'ACTIVE',
  };
  const alice: StoredMember = {
    memberId: 'MEM-ALICE',
    username: 'alice',
    password: 'alice1234',
    firstName: 'Alice',
    lastName: 'Liddell',
    email: 'alice@example.com',
    dateOfBirth: '1998-04-12',
    status: 'ACTIVE',
  };
  for (const m of [admin, alice]) {
    members.set(m.memberId, m);
    usernameIndex.set(m.username, m.memberId);
  }

  // Seed a company owned by alice so company screens are usable on first run.
  const company: CompanyDto = {
    companyId: 'CO-SAMI',
    companyName: 'Sami Productions',
    status: 'ACTIVE',
    contactDetails: 'contact@sami.example',
  };
  companies.set(company.companyId, company);
  companyRoles.set(company.companyId, [
    {
      memberId: alice.memberId,
      username: alice.username,
      roleType: 'OWNER',
      permissions: ['MANAGE_EVENTS', 'MANAGE_POLICIES', 'MANAGE_ROLES', 'VIEW_REPORTS'],
    },
  ]);
  memberCompanies.set(alice.memberId, new Set([company.companyId]));
}
seed();

function rolesFor(memberId: string): ('ADMIN' | 'MEMBER' | 'COMPANY_OWNER' | 'COMPANY_MANAGER')[] {
  const m = members.get(memberId);
  if (!m) return ['MEMBER'];
  const roles: ('ADMIN' | 'MEMBER' | 'COMPANY_OWNER' | 'COMPANY_MANAGER')[] = [];
  if (m.username === 'admin') roles.push('ADMIN');
  else roles.push('MEMBER');
  const owned = memberCompanies.get(memberId);
  if (owned && owned.size > 0) {
    // promote based on highest role in any company
    let hasOwner = false;
    let hasManager = false;
    for (const cid of owned) {
      const rs = companyRoles.get(cid) ?? [];
      const r = rs.find((x) => x.memberId === memberId);
      if (r?.roleType === 'OWNER') hasOwner = true;
      else if (r?.roleType === 'MANAGER') hasManager = true;
    }
    if (hasOwner) roles.push('COMPANY_OWNER');
    else if (hasManager) roles.push('COMPANY_MANAGER');
  }
  return roles;
}

function issueToken(memberId: string, roles: string[]): LoginResponse {
  // Fake JWT: header.payload.signature where payload encodes claims so the UI can decode roles.
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const exp = Math.floor(Date.now() / 1000) + 60 * 60 * 8;
  const payload = btoa(JSON.stringify({ sub: memberId, memberId, roles, exp }));
  const token = `${header}.${payload}.mock`;
  const expiresAt = new Date(exp * 1000).toISOString();
  tokens.set(token, { memberId, expiresAt });
  return { token, memberId, expiresAt };
}

function stripPassword(m: StoredMember): MemberDto {
  // Avoid leaking the password field to the UI even in mocks.
  const { password: _password, ...dto } = m;
  void _password;
  return dto;
}

function err(status: number, message: string, path: string) {
  return HttpResponse.json(
    {
      timestamp: new Date().toISOString(),
      status,
      errorType: 'DomainException',
      message,
      path,
    },
    { status },
  );
}

function memberFromRequest(request: Request): string | null {
  const auth = request.headers.get('Authorization');
  if (!auth?.startsWith('Bearer ')) return null;
  const token = auth.slice('Bearer '.length);
  return tokens.get(token)?.memberId ?? null;
}

function recalcTotals(order: OrderDto) {
  order.totalBeforeDiscount = order.items.reduce((s, i) => s + i.unitPrice, 0);
  order.totalAfterDiscount = order.totalBeforeDiscount;
}

// ---------- Sample catalog ----------
const catalog: EventDetailDto[] = [
  {
    eventId: 'EVT-1',
    name: 'Rick and Morty: The Musical',
    companyName: 'Sami Productions',
    dateTime: '2026-06-20T20:00:00Z',
    venueName: 'Grand Hall',
    status: 'PUBLISHED',
    startingPrice: 120,
    description: 'Wubba lubba dub dub!',
    zones: [
      { zoneId: 'Z-1', name: 'Floor', type: 'STANDING', basePrice: 120, available: 200, capacity: 500 },
      { zoneId: 'Z-2', name: 'Balcony', type: 'SEATED', basePrice: 220, available: 80, capacity: 200 },
    ],
  },
  {
    eventId: 'EVT-2',
    name: 'Cosmic Symphony',
    companyName: 'Stellar Events',
    dateTime: '2026-07-04T19:30:00Z',
    venueName: 'Open Sky Arena',
    status: 'PUBLISHED',
    startingPrice: 80,
    zones: [
      { zoneId: 'Z-3', name: 'GA', type: 'STANDING', basePrice: 80, available: 1500, capacity: 2000 },
    ],
  },
  {
    eventId: 'EVT-3',
    name: 'Neon Tides Festival',
    companyName: 'Stellar Events',
    dateTime: '2026-08-15T21:00:00Z',
    venueName: 'Harborfront Park',
    status: 'PUBLISHED',
    startingPrice: 60,
    description: 'Three nights of electronic music under the stars.',
    zones: [
      { zoneId: 'Z-4', name: 'General', type: 'STANDING', basePrice: 60, available: 3000, capacity: 5000 },
      { zoneId: 'Z-5', name: 'VIP', type: 'SEATED', basePrice: 180, available: 100, capacity: 250 },
    ],
  },
];

function summarize(e: EventDetailDto): EventSummaryDto {
  const { zones: _z, description: _d, ...rest } = e;
  void _z;
  void _d;
  return rest;
}

export const handlers = [
  // -------- Auth --------
  http.post('/api/auth/register', async ({ request }) => {
    await delay(150);
    const body = (await request.json()) as RegisterRequest;
    if (usernameIndex.has(body.username)) {
      return err(400, 'Username is already taken.', '/api/auth/register');
    }
    const memberId = `MEM-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
    const stored: StoredMember = {
      memberId,
      username: body.username,
      password: body.plaintextPassword,
      firstName: body.firstName,
      lastName: body.lastName,
      email: body.email,
      dateOfBirth: body.dateOfBirth,
      status: 'ACTIVE',
    };
    members.set(memberId, stored);
    usernameIndex.set(body.username, memberId);
    return HttpResponse.json(stripPassword(stored), { status: 201 });
  }),

  http.post('/api/auth/login', async ({ request }) => {
    await delay(150);
    const body = (await request.json()) as LoginRequest;
    const memberId = usernameIndex.get(body.username);
    const member = memberId ? members.get(memberId) : undefined;
    if (!member || member.password !== body.plaintextPassword) {
      return err(401, 'Invalid username or password.', '/api/auth/login');
    }
    if (member.status === 'SUSPENDED') {
      return err(403, 'Your account is suspended.', '/api/auth/login');
    }
    return HttpResponse.json(issueToken(member.memberId, rolesFor(member.memberId)));
  }),

  // -------- Members --------
  http.get('/api/members/:targetId', ({ params }) => {
    const m = members.get(String(params.targetId));
    if (!m) return err(404, 'Member not found.', `/api/members/${params.targetId}`);
    return HttpResponse.json(stripPassword(m));
  }),

  // -------- Catalog --------
  http.get('/api/events', ({ request }) => {
    const url = new URL(request.url);
    const maxPrice = url.searchParams.get('maxPrice');
    const artist = url.searchParams.get('artist')?.toLowerCase();
    let list = catalog;
    if (maxPrice) {
      const cap = Number(maxPrice);
      list = list.filter((e) => (e.startingPrice ?? 0) <= cap);
    }
    if (artist) {
      list = list.filter(
        (e) => e.name.toLowerCase().includes(artist) || e.companyName.toLowerCase().includes(artist),
      );
    }
    return HttpResponse.json(list.map(summarize));
  }),

  http.get('/api/events/:eventId', ({ params }) => {
    const ev = catalog.find((e) => e.eventId === params.eventId);
    if (!ev) return err(404, 'Event not found.', `/api/events/${params.eventId}`);
    return HttpResponse.json(ev);
  }),

  // -------- Queue --------
  http.post('/api/events/:eventId/queue/entries', ({ request, params }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', String(request.url));
    admissions.add(`${params.eventId}:${memberId}`);
    return HttpResponse.json(null, { status: 201 });
  }),

  http.get('/api/events/:eventId/queue/status', ({ request, params }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', String(request.url));
    const isAdmitted = admissions.has(`${params.eventId}:${memberId}`);
    const dto: QueueStatusDto = { isAdmitted, position: isAdmitted ? 0 : 12 };
    return HttpResponse.json(dto);
  }),

  http.delete('/api/events/:eventId/queue/admissions', ({ request, params }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', String(request.url));
    admissions.delete(`${params.eventId}:${memberId}`);
    return HttpResponse.json(null, { status: 204 });
  }),

  // -------- Orders --------
  http.post('/api/orders/active', async ({ request }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', '/api/orders/active');
    const body = (await request.json()) as { eventId: string };
    const ev = catalog.find((e) => e.eventId === body.eventId);
    if (!ev) return err(404, 'Event not found.', '/api/orders/active');
    // Find existing active order for this buyer+event, else create.
    const existing = [...orders.values()].find(
      (o) => o.eventId === body.eventId && o.status === 'ACTIVE',
    );
    if (existing) {
      return HttpResponse.json({ orderId: existing.orderId, expiresAt: existing.expiresAt });
    }
    const orderId = `ORD-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
    const expiresAt = new Date(Date.now() + 15 * 60_000).toISOString();
    const order: OrderDto = {
      orderId,
      eventId: body.eventId,
      expiresAt,
      items: [],
      totalBeforeDiscount: 0,
      totalAfterDiscount: 0,
      status: 'ACTIVE',
    };
    orders.set(orderId, order);
    return HttpResponse.json({ orderId, expiresAt });
  }),

  http.get('/api/orders/:orderId', ({ params }) => {
    const o = orders.get(String(params.orderId));
    if (!o) return err(404, 'Order not found.', `/api/orders/${params.orderId}`);
    return HttpResponse.json(o);
  }),

  http.post('/api/orders/:orderId/items', async ({ request, params }) => {
    const order = orders.get(String(params.orderId));
    if (!order) return err(404, 'Order not found.', String(request.url));
    if (order.status !== 'ACTIVE') return err(400, 'This order is no longer active.', String(request.url));
    const body = (await request.json()) as AddItemRequest;
    const ev = catalog.find((e) => e.eventId === order.eventId);
    const zone = ev?.zones.find((z) => z.zoneId === body.zoneId);
    if (!zone) return err(404, 'Zone not found.', String(request.url));
    if (order.items.some((i) => i.seatId === body.seatId)) {
      return err(400, 'That seat is already in your cart.', String(request.url));
    }
    const item: OrderItemDto = {
      zoneId: zone.zoneId,
      seatId: body.seatId,
      seatLabel: body.seatId,
      unitPrice: zone.basePrice,
    };
    order.items.push(item);
    recalcTotals(order);
    return HttpResponse.json(order);
  }),

  http.delete('/api/orders/:orderId/items/:seatId', ({ request, params }) => {
    const order = orders.get(String(params.orderId));
    if (!order) return err(404, 'Order not found.', String(request.url));
    order.items = order.items.filter((i) => i.seatId !== String(params.seatId));
    recalcTotals(order);
    return HttpResponse.json(order);
  }),

  http.post('/api/orders/:orderId/checkout', async ({ request, params }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', String(request.url));
    const order = orders.get(String(params.orderId));
    if (!order) return err(404, 'Order not found.', String(request.url));
    if (order.items.length === 0) return err(400, 'Your cart is empty.', String(request.url));
    const body = (await request.json()) as CheckoutRequest;
    if (!body.paymentToken) return err(400, 'Payment method is required.', String(request.url));

    // Toy discount: any code "PROMO10" gives 10%.
    if (body.discountCode === 'PROMO10') {
      order.totalAfterDiscount = Math.round(order.totalBeforeDiscount * 0.9 * 100) / 100;
    } else if (body.discountCode) {
      return err(400, 'Invalid discount code.', String(request.url));
    }

    const ev = catalog.find((e) => e.eventId === order.eventId);
    const recordId = `REC-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
    const record: PurchaseRecordDto = {
      recordId,
      eventId: order.eventId,
      eventName: ev?.name ?? 'Unknown event',
      purchasedAt: new Date().toISOString(),
      totalPaid: order.totalAfterDiscount,
      items: order.items.map<PurchasedItemDto>((i) => ({
        zoneName: ev?.zones.find((z) => z.zoneId === i.zoneId)?.name ?? i.zoneId,
        seatLabel: i.seatLabel ?? i.seatId,
        unitPrice: i.unitPrice,
      })),
    };
    const buyerHistory = history.get(memberId) ?? [];
    buyerHistory.unshift(record);
    history.set(memberId, buyerHistory);
    order.status = 'CHECKED_OUT';
    return HttpResponse.json({ recordId });
  }),

  // -------- History --------
  http.get('/api/history/receipts', ({ request }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', '/api/history/receipts');
    return HttpResponse.json(history.get(memberId) ?? []);
  }),

  http.get('/api/history/receipts/:recordId', ({ request, params }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', String(request.url));
    const list = history.get(memberId) ?? [];
    const rec = list.find((r) => r.recordId === params.recordId);
    if (!rec) return err(404, 'Receipt not found.', String(request.url));
    return HttpResponse.json(rec);
  }),

  // -------- Lottery --------
  http.post('/api/events/:eventId/lottery/registrations', ({ request, params }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', String(request.url));
    const key = String(params.eventId);
    const set = lotteryRegs.get(key) ?? new Set<string>();
    if (set.has(memberId)) return err(400, 'You are already registered for this lottery.', String(request.url));
    set.add(memberId);
    lotteryRegs.set(key, set);
    return HttpResponse.json({ registrationId: `LOT-${Math.random().toString(36).slice(2, 8).toUpperCase()}` }, { status: 201 });
  }),

  // -------- Companies --------
  http.get('/api/companies', ({ request }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', '/api/companies');
    const owned = memberCompanies.get(memberId) ?? new Set<string>();
    const list = [...companies.values()].filter((c) => owned.has(c.companyId));
    return HttpResponse.json(list);
  }),

  http.post('/api/companies', async ({ request }) => {
    const memberId = memberFromRequest(request);
    if (!memberId) return err(401, 'Authentication required.', '/api/companies');
    const body = (await request.json()) as CreateCompanyRequest;
    const companyId = `CO-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
    const company: CompanyDto = {
      companyId,
      companyName: body.companyName,
      status: 'ACTIVE',
      contactDetails: body.contactDetails,
    };
    companies.set(companyId, company);
    const member = members.get(memberId);
    companyRoles.set(companyId, [
      {
        memberId,
        username: member?.username,
        roleType: 'OWNER',
        permissions: ['MANAGE_EVENTS', 'MANAGE_POLICIES', 'MANAGE_ROLES', 'VIEW_REPORTS'],
      },
    ]);
    const owned = memberCompanies.get(memberId) ?? new Set<string>();
    owned.add(companyId);
    memberCompanies.set(memberId, owned);
    return HttpResponse.json(company, { status: 201 });
  }),

  http.get('/api/companies/:companyId', ({ params }) => {
    const c = companies.get(String(params.companyId));
    if (!c) return err(404, 'Company not found.', String(params.companyId));
    return HttpResponse.json(c);
  }),

  http.patch('/api/companies/:companyId/status', async ({ request, params }) => {
    const c = companies.get(String(params.companyId));
    if (!c) return err(404, 'Company not found.', String(request.url));
    const body = (await request.json()) as CompanyStatusUpdate;
    c.status = body.status;
    return HttpResponse.json(c);
  }),

  http.get('/api/companies/:companyId/reports/sales', ({ params }) => {
    const companyId = String(params.companyId);
    const c = companies.get(companyId);
    if (!c) return err(404, 'Company not found.', `/api/companies/${companyId}/reports/sales`);
    // Aggregate from history across all members for this company's events.
    const events = catalog.filter((e) => e.companyName === c.companyName);
    const rows: SalesReportRow[] = events.map((e) => {
      let tickets = 0;
      let gross = 0;
      for (const recs of history.values()) {
        for (const r of recs) {
          if (r.eventId !== e.eventId) continue;
          tickets += r.items.length;
          gross += r.totalPaid;
        }
      }
      return { eventId: e.eventId, eventName: e.name, ticketsSold: tickets, grossRevenue: gross };
    });
    return HttpResponse.json(rows);
  }),

  // -------- Roles --------
  http.get('/api/companies/:companyId/roles', ({ params }) => {
    const rs = companyRoles.get(String(params.companyId)) ?? [];
    return HttpResponse.json(rs);
  }),

  http.post('/api/companies/:companyId/roles', async ({ request, params }) => {
    const companyId = String(params.companyId);
    if (!companies.has(companyId)) return err(404, 'Company not found.', String(request.url));
    const body = (await request.json()) as AppointRoleRequest;
    const target = members.get(body.targetMemberId);
    if (!target) return err(404, 'Target member not found.', String(request.url));
    const rs = companyRoles.get(companyId) ?? [];
    if (rs.some((r) => r.memberId === body.targetMemberId)) {
      return err(400, 'That member already has a role in this company.', String(request.url));
    }
    const role: CompanyRoleDto = {
      memberId: target.memberId,
      username: target.username,
      roleType: body.roleType,
      permissions: body.permissionsList,
    };
    rs.push(role);
    companyRoles.set(companyId, rs);
    const owned = memberCompanies.get(target.memberId) ?? new Set<string>();
    owned.add(companyId);
    memberCompanies.set(target.memberId, owned);
    return HttpResponse.json(role, { status: 201 });
  }),

  http.delete('/api/companies/:companyId/roles/:memberId', ({ request, params }) => {
    const companyId = String(params.companyId);
    const memberId = String(params.memberId);
    const rs = companyRoles.get(companyId) ?? [];
    const filtered = rs.filter((r) => r.memberId !== memberId);
    companyRoles.set(companyId, filtered);
    const owned = memberCompanies.get(memberId);
    if (owned) {
      owned.delete(companyId);
      if (owned.size === 0) memberCompanies.delete(memberId);
    }
    void request;
    return HttpResponse.json(null, { status: 204 });
  }),

  // -------- Events under company --------
  http.post('/api/companies/:companyId/events', async ({ request, params }) => {
    const companyId = String(params.companyId);
    const c = companies.get(companyId);
    if (!c) return err(404, 'Company not found.', String(request.url));
    const body = (await request.json()) as CreateEventRequest;
    const eventId = `EVT-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
    const event: EventDetailDto = {
      eventId,
      name: body.name,
      companyName: c.companyName,
      dateTime: body.dateTime,
      venueName: body.venueName,
      status: 'PUBLISHED',
      description: body.description,
      startingPrice: body.zones.length > 0 ? Math.min(...body.zones.map((z) => z.basePrice)) : undefined,
      zones: body.zones.map((z, i) => ({
        zoneId: `${eventId}-Z${i + 1}`,
        name: z.name,
        type: z.type,
        basePrice: z.basePrice,
        available: z.capacity,
        capacity: z.capacity,
      })),
    };
    catalog.push(event);
    return HttpResponse.json(event, { status: 201 });
  }),

  // -------- Policies --------
  http.get('/api/companies/:companyId/policies', ({ params }) => {
    const b = companyPolicies.get(String(params.companyId)) ?? { discount: null, purchase: null };
    return HttpResponse.json(b);
  }),
  http.put('/api/companies/:companyId/policies', async ({ request, params }) => {
    const body = (await request.json()) as PolicyBundle;
    companyPolicies.set(String(params.companyId), body);
    return HttpResponse.json(body);
  }),

  http.get('/api/events/:eventId/policies', ({ params }) => {
    const b = eventPolicies.get(String(params.eventId)) ?? { discount: null, purchase: null };
    return HttpResponse.json(b);
  }),
  http.put('/api/events/:eventId/policies', async ({ request, params }) => {
    const body = (await request.json()) as PolicyBundle;
    eventPolicies.set(String(params.eventId), body);
    return HttpResponse.json(body);
  }),

  // -------- Admin --------
  http.delete('/api/admin/companies/:companyId', ({ params }) => {
    const c = companies.get(String(params.companyId));
    if (!c) return err(404, 'Company not found.', String(params.companyId));
    c.status = 'CLOSED';
    return HttpResponse.json(null, { status: 204 });
  }),

  http.delete('/api/admin/members/:memberId', ({ params }) => {
    const memberId = String(params.memberId);
    const m = members.get(memberId);
    if (!m) return err(404, 'Member not found.', memberId);
    m.status = 'DEACTIVATED';
    usernameIndex.delete(m.username);
    return HttpResponse.json(null, { status: 204 });
  }),

  http.post('/api/admin/members/:memberId/suspensions', async ({ request, params }) => {
    const memberId = String(params.memberId);
    const m = members.get(memberId);
    if (!m) return err(404, 'Member not found.', String(request.url));
    const body = (await request.json()) as SuspendRequest;
    const now = new Date();
    const dto: SuspensionDto = {
      memberId,
      username: m.username,
      suspendedAt: now.toISOString(),
      durationMinutes: body.durationMinutes,
      endsAt: body.durationMinutes === null
        ? null
        : new Date(now.getTime() + body.durationMinutes * 60_000).toISOString(),
      reason: body.reason,
    };
    suspensions.set(memberId, dto);
    m.status = 'SUSPENDED';
    return HttpResponse.json(dto, { status: 201 });
  }),

  http.delete('/api/admin/members/:memberId/suspensions', ({ params }) => {
    const memberId = String(params.memberId);
    const m = members.get(memberId);
    if (!m) return err(404, 'Member not found.', memberId);
    suspensions.delete(memberId);
    if (m.status === 'SUSPENDED') m.status = 'ACTIVE';
    return HttpResponse.json(null, { status: 204 });
  }),

  http.get('/api/admin/members/suspensions', () => {
    return HttpResponse.json([...suspensions.values()]);
  }),

  http.get('/api/admin/history', () => {
    const rows: GlobalHistoryRow[] = [];
    for (const [memberId, recs] of history.entries()) {
      const m = members.get(memberId);
      for (const r of recs) {
        rows.push({
          recordId: r.recordId,
          buyerUsername: m?.username ?? memberId,
          eventName: r.eventName,
          purchasedAt: r.purchasedAt,
          totalPaid: r.totalPaid,
        });
      }
    }
    rows.sort((a, b) => (a.purchasedAt < b.purchasedAt ? 1 : -1));
    return HttpResponse.json(rows);
  }),
];
