import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiRequest } from '../api/client';
import { PageResponse } from '../types/masterData';
import {
  Card,
  Button,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  Badge,
  Modal,
  Pagination,
  DEFAULT_PAGE_SIZE,
} from '@hms/ui';
import { Search, RotateCcw, Eye, ShieldAlert, Filter } from 'lucide-react';

export interface AuditLogItem {
  id: number;
  userId?: number | null;
  action: string;
  entityType: string;
  entityId?: string | null;
  ipAddress?: string | null;
  details?: string | null;
  createdAt: string;
}

export const AuditLogsPage: React.FC = () => {
  const [page, setPage] = useState(0);

  // Filters state
  const [userIdInput, setUserIdInput] = useState('');
  const [actionInput, setActionInput] = useState('');
  const [entityTypeInput, setEntityTypeInput] = useState('');
  const [startDateInput, setStartDateInput] = useState('');
  const [endDateInput, setEndDateInput] = useState('');

  // Applied filters
  const [appliedFilters, setAppliedFilters] = useState<{
    userId?: string;
    action?: string;
    entityType?: string;
    startDate?: string;
    endDate?: string;
  }>({});

  const [selectedLog, setSelectedLog] = useState<AuditLogItem | null>(null);

  // Build query string
  const queryParams = new URLSearchParams();
  queryParams.append('page', page.toString());
  queryParams.append('size', DEFAULT_PAGE_SIZE.toString());
  if (appliedFilters.userId) queryParams.append('userId', appliedFilters.userId);
  if (appliedFilters.action) queryParams.append('action', appliedFilters.action);
  if (appliedFilters.entityType) queryParams.append('entityType', appliedFilters.entityType);
  if (appliedFilters.startDate) queryParams.append('startDate', appliedFilters.startDate);
  if (appliedFilters.endDate) queryParams.append('endDate', appliedFilters.endDate);

  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<AuditLogItem>>({
    queryKey: ['audit-logs', page, appliedFilters, DEFAULT_PAGE_SIZE],
    queryFn: () => apiRequest<PageResponse<AuditLogItem>>(`/api/admin/audit-logs?${queryParams.toString()}`),
  });

  const handleApplyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setAppliedFilters({
      userId: userIdInput.trim() || undefined,
      action: actionInput.trim() || undefined,
      entityType: entityTypeInput.trim() || undefined,
      startDate: startDateInput ? new Date(startDateInput).toISOString().slice(0, 19) : undefined,
      endDate: endDateInput ? new Date(endDateInput).toISOString().slice(0, 19) : undefined,
    });
  };

  const handleResetFilters = () => {
    setUserIdInput('');
    setActionInput('');
    setEntityTypeInput('');
    setStartDateInput('');
    setEndDateInput('');
    setAppliedFilters({});
    setPage(0);
  };

  const getActionBadgeVariant = (action: string) => {
    if (action.includes('FAILED') || action.includes('DEACTIVATE') || action.includes('DELETE') || action.includes('DISABLE')) {
      return 'danger';
    }
    if (action.includes('SUCCESS') || action.includes('LOGIN')) {
      return 'success';
    }
    if (action.includes('CREATE') || action.includes('UPDATE')) {
      return 'info';
    }
    return 'neutral';
  };

  const formatTimestamp = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return iso;
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>
            System Audit Log
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            Immutable administrative trail tracking user security events, data modifications, and clinical operations
          </p>
        </div>
      </div>

      {/* Filter Bar */}
      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleApplyFilters}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: '12px',
              marginBottom: '14px',
            }}
          >
            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#475569', marginBottom: '4px' }}>
                User ID
              </label>
              <input
                type="number"
                placeholder="e.g. 1"
                value={userIdInput}
                onChange={(e) => setUserIdInput(e.target.value)}
                style={{
                  width: '100%',
                  padding: '7px 10px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  fontSize: '13px',
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#475569', marginBottom: '4px' }}>
                Action
              </label>
              <input
                type="text"
                placeholder="e.g. LOGIN_SUCCESS"
                value={actionInput}
                onChange={(e) => setActionInput(e.target.value)}
                style={{
                  width: '100%',
                  padding: '7px 10px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  fontSize: '13px',
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#475569', marginBottom: '4px' }}>
                Entity Type
              </label>
              <input
                type="text"
                placeholder="e.g. USER, BRANCH"
                value={entityTypeInput}
                onChange={(e) => setEntityTypeInput(e.target.value)}
                style={{
                  width: '100%',
                  padding: '7px 10px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  fontSize: '13px',
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#475569', marginBottom: '4px' }}>
                Start Date
              </label>
              <input
                type="datetime-local"
                value={startDateInput}
                onChange={(e) => setStartDateInput(e.target.value)}
                style={{
                  width: '100%',
                  padding: '6px 8px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  fontSize: '12px',
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#475569', marginBottom: '4px' }}>
                End Date
              </label>
              <input
                type="datetime-local"
                value={endDateInput}
                onChange={(e) => setEndDateInput(e.target.value)}
                style={{
                  width: '100%',
                  padding: '6px 8px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  fontSize: '12px',
                }}
              />
            </div>
          </div>

          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
            <Button type="button" variant="outline" size="sm" onClick={handleResetFilters}>
              <RotateCcw size={14} />
              Reset
            </Button>
            <Button type="submit" variant="primary" size="sm">
              <Filter size={14} />
              Apply Filters
            </Button>
          </div>
        </form>
      </Card>

      {/* Main Table Card */}
      <Card style={{ padding: 0, overflow: 'hidden' }}>
        {isLoading && (
          <div style={{ padding: '40px', textAlign: 'center', color: '#64748b', fontSize: '14px' }}>
            Loading audit records...
          </div>
        )}

        {isError && (
          <div style={{ padding: '40px', textAlign: 'center', color: '#ef4444', fontSize: '14px' }}>
            Failed to load audit records. {error instanceof Error ? error.message : 'Please check your connection.'}
          </div>
        )}

        {!isLoading && !isError && data && data.content.length === 0 && (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            <ShieldAlert size={36} color="#94a3b8" style={{ marginBottom: '12px' }} />
            <div style={{ fontSize: '16px', fontWeight: 600, color: '#0f172a', marginBottom: '4px' }}>
              No audit records found
            </div>
            <div style={{ fontSize: '13px' }}>Try adjusting your filters or search criteria.</div>
          </div>
        )}

        {!isLoading && !isError && data && data.content.length > 0 && (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead style={{ width: '80px' }}>ID</TableHead>
                  <TableHead style={{ width: '170px' }}>Timestamp</TableHead>
                  <TableHead style={{ width: '160px' }}>Action</TableHead>
                  <TableHead style={{ width: '140px' }}>Entity</TableHead>
                  <TableHead style={{ width: '100px' }}>User ID</TableHead>
                  <TableHead style={{ width: '130px' }}>IP Address</TableHead>
                  <TableHead>Details</TableHead>
                  <TableHead style={{ width: '90px', textAlign: 'right' }}>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell style={{ fontWeight: 600, color: '#64748b' }}>#{log.id}</TableCell>
                    <TableCell style={{ fontSize: '12px', color: '#475569' }}>
                      {formatTimestamp(log.createdAt)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={getActionBadgeVariant(log.action)}>{log.action}</Badge>
                    </TableCell>
                    <TableCell>
                      <div style={{ fontSize: '13px', fontWeight: 600, color: '#0f172a' }}>
                        {log.entityType}
                      </div>
                      {log.entityId && (
                        <div style={{ fontSize: '11px', color: '#64748b' }}>ID: {log.entityId}</div>
                      )}
                    </TableCell>
                    <TableCell>
                      {log.userId ? (
                        <span style={{ fontSize: '13px', fontWeight: 600, color: '#0f172a' }}>
                          User #{log.userId}
                        </span>
                      ) : (
                        <span style={{ fontSize: '12px', color: '#94a3b8', fontStyle: 'italic' }}>
                          System / Anon
                        </span>
                      )}
                    </TableCell>
                    <TableCell style={{ fontSize: '12px', color: '#64748b' }}>
                      {log.ipAddress || '—'}
                    </TableCell>
                    <TableCell>
                      <div
                        style={{
                          fontSize: '12px',
                          color: '#64748b',
                          maxWidth: '240px',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {log.details || '—'}
                      </div>
                    </TableCell>
                    <TableCell style={{ textAlign: 'right' }}>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setSelectedLog(log)}
                        style={{ padding: '4px 8px', fontSize: '12px' }}
                      >
                        <Eye size={13} />
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Pagination
              pageNumber={data.pageNumber}
              pageSize={data.pageSize}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              isFirst={data.isFirst}
              isLast={data.isLast}
              onPageChange={(newPage) => setPage(newPage)}
            />
          </>
        )}
      </Card>

      {/* Log Details Modal */}
      {selectedLog && (
        <Modal
          isOpen={true}
          title={`Audit Log Record #${selectedLog.id}`}
          onClose={() => setSelectedLog(null)}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '13px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 600, color: '#64748b' }}>
                  ACTION
                </label>
                <div style={{ marginTop: '4px' }}>
                  <Badge variant={getActionBadgeVariant(selectedLog.action)}>{selectedLog.action}</Badge>
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 600, color: '#64748b' }}>
                  TIMESTAMP
                </label>
                <div style={{ marginTop: '4px', fontWeight: 600, color: '#0f172a' }}>
                  {formatTimestamp(selectedLog.createdAt)}
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 600, color: '#64748b' }}>
                  ENTITY TYPE
                </label>
                <div style={{ marginTop: '4px', fontWeight: 600, color: '#0f172a' }}>
                  {selectedLog.entityType}
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 600, color: '#64748b' }}>
                  ENTITY ID
                </label>
                <div style={{ marginTop: '4px', fontWeight: 600, color: '#0f172a' }}>
                  {selectedLog.entityId || 'N/A'}
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 600, color: '#64748b' }}>
                  USER ID
                </label>
                <div style={{ marginTop: '4px', fontWeight: 600, color: '#0f172a' }}>
                  {selectedLog.userId ? `User #${selectedLog.userId}` : 'Anonymous / Unauthenticated'}
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 600, color: '#64748b' }}>
                  IP ADDRESS
                </label>
                <div style={{ marginTop: '4px', fontWeight: 600, color: '#0f172a' }}>
                  {selectedLog.ipAddress || 'Not recorded'}
                </div>
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '11px', fontWeight: 600, color: '#64748b', marginBottom: '6px' }}>
                AUDIT DETAILS / PAYLOAD SUMMARY
              </label>
              <div
                style={{
                  padding: '12px',
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '6px',
                  fontFamily: 'monospace',
                  fontSize: '12px',
                  color: '#334155',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  maxHeight: '200px',
                  overflowY: 'auto',
                }}
              >
                {selectedLog.details || '(No additional details recorded)'}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '12px' }}>
              <Button variant="outline" size="sm" onClick={() => setSelectedLog(null)}>
                Close
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
