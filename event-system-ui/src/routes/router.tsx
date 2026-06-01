import { createBrowserRouter, Outlet } from 'react-router-dom';
import { Placeholder } from '../components/Placeholder';

// Minimal shell — Student A will replace this with the real Layout component
function MinimalShell() {
  return (
    <div>
      <header style={{ padding: '1rem', borderBottom: '1px solid #ccc' }}>
        <strong>EventSystem</strong>
        <span style={{ opacity: 0.5, marginLeft: '1rem' }}>
          (base scaffold — real nav coming soon)
        </span>
      </header>
      <main style={{ padding: '1rem' }}>
        <Outlet />
      </main>
    </div>
  );
}

export const router = createBrowserRouter([
  {
    element: <MinimalShell />,
    children: [
      { path: '/',                    element: <Placeholder title="Home" /> },
      { path: '/login',              element: <Placeholder title="Login" /> },
      { path: '/register',           element: <Placeholder title="Register" /> },
      { path: '/events',             element: <Placeholder title="Event Catalog" /> },
      { path: '/events/:eventId',    element: <Placeholder title="Event Detail" /> },
      { path: '/events/:eventId/queue',     element: <Placeholder title="Queue" /> },
      { path: '/events/:eventId/policies',  element: <Placeholder title="Event Policies" /> },
      { path: '/orders/:orderId',    element: <Placeholder title="Order" /> },
      { path: '/history',            element: <Placeholder title="Purchase History" /> },
      { path: '/history/:recordId',  element: <Placeholder title="Receipt Detail" /> },
      { path: '/notifications',      element: <Placeholder title="Notifications" /> },
      { path: '/companies',          element: <Placeholder title="Companies" /> },
      { path: '/companies/:companyId',          element: <Placeholder title="Company Detail" /> },
      { path: '/companies/:companyId/roles',    element: <Placeholder title="Roles" /> },
      { path: '/companies/:companyId/policies', element: <Placeholder title="Company Policies" /> },
      { path: '/companies/:companyId/events/new', element: <Placeholder title="Create Event" /> },
      { path: '/admin',              element: <Placeholder title="Admin Dashboard" /> },
      { path: '/admin/suspensions',  element: <Placeholder title="Suspensions" /> },
      { path: '/admin/members',      element: <Placeholder title="Ban Member" /> },
      { path: '/admin/companies',    element: <Placeholder title="Close Company" /> },
      { path: '/admin/history',      element: <Placeholder title="Global History" /> },
      { path: '*',                   element: <Placeholder title="Not Found" /> },
    ],
  },
]);
