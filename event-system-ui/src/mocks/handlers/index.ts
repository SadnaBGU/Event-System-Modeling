import type { RequestHandler } from 'msw';
import { policyHandlers } from './policies';

// Each student adds their handlers here using the spread pattern:
// export const handlers: RequestHandler[] = [
//   ...authHandlers,          // Student A
//   ...policyHandlers,        // Student B
//   ...notificationHandlers,  // Student C
// ];

export const handlers: RequestHandler[] = [
  ...policyHandlers,
];