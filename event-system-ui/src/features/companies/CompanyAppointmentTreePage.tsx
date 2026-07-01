import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { companiesApi } from '../../api/endpoints/companies';
import type { AppointmentManagerNodeDto, AppointmentOwnerNodeDto } from '../../types/api';
import '../../components/common.css';

function OwnerNodeView({ node, depth }: { node: AppointmentOwnerNodeDto; depth: number }) {
  return (
    <li style={{ marginTop: depth === 0 ? 0 : '0.45rem' }}>
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="pill ACTIVE" style={{ fontSize: '0.7rem' }}>OWNER</span>
        <code>{node.memberUsername}</code>
        {node.appointerId && (
          <span className="meta" style={{ margin: 0 }}>appointed by <code>{node.appointerUsername}</code></span>
        )}
      </div>

      {(node.managers.length > 0 || node.owners.length > 0) && (
        <ul style={{ marginTop: '0.4rem', paddingLeft: '1.1rem', borderLeft: '1px solid #30363d' }}>
          {node.managers.map((manager) => (
            <ManagerNodeView key={`m-${manager.memberId}`} node={manager} depth={depth + 1} />
          ))}
          {node.owners.map((owner) => (
            <OwnerNodeView key={`o-${owner.memberId}`} node={owner} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  );
}

function ManagerNodeView({ node, depth }: { node: AppointmentManagerNodeDto; depth: number }) {
  return (
    <li style={{ marginTop: '0.45rem' }}>
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="pill SUSPENDED" style={{ fontSize: '0.7rem' }}>MANAGER</span>
        <code>{node.memberUsername}</code>
        <span className="meta" style={{ margin: 0 }}>appointed by <code>{node.appointerUsername}</code></span>
      </div>
      <p className="meta" style={{ marginTop: '0.2rem' }}>
        Permissions: {node.permissions.length > 0 ? node.permissions.join(', ') : 'None'}
      </p>
        {/* // member username */}
      {node.managers.length > 0 && (
        <ul style={{ marginTop: '0.4rem', paddingLeft: '1.1rem', borderLeft: '1px solid #30363d' }}>
          {node.managers.map((child) => (
            <ManagerNodeView key={`m-${child.memberId}`} node={child} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  );
}

export function CompanyAppointmentTreePage() {
  const { companyId = '' } = useParams();

  const treeQ = useQuery({
    queryKey: ['company-appointment-tree', companyId],
    queryFn: () => companiesApi.appointmentTree(companyId),
    enabled: !!companyId,
  });

  if (treeQ.isLoading) return <p>Loading…</p>;
  if (treeQ.isError || !treeQ.data) return <p className="empty">Could not load appointment tree.</p>;

  const tree = treeQ.data;

  return (
    <section>
      <Link to={`/companies/${companyId}`} className="btn ghost" style={{ marginBottom: '1rem' }}>
        ← Company
      </Link>
      <h1 className="page-title">Appointment tree</h1>
      <p className="meta">{tree.companyName}</p>

      <div style={{ marginTop: '1rem' }}>
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          <OwnerNodeView node={tree.root} depth={0} />
        </ul>
      </div>
    </section>
  );
}
