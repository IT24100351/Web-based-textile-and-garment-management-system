import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import {
  changeAdminUserRole,
  changeAdminUserStatus,
  createInternalUser,
  getAdminUser,
  getAdminUserApiError,
  getAdminUsers,
  type AdminUser,
} from "../api/adminUsers";
import type { UserRole } from "../api/auth";
import { useAuth } from "../auth/useAuth";
import { roleLabels } from "../navigation/navigation";

const allRoles: UserRole[] = [
  "ADMINISTRATOR",
  "SUPPLIER",
  "INVENTORY_MANAGER",
  "PRODUCTION_MANAGER",
  "SALES_OFFICER",
  "CUSTOMER",
];

const internalRoles: Array<Exclude<UserRole, "CUSTOMER">> = [
  "ADMINISTRATOR",
  "SUPPLIER",
  "INVENTORY_MANAGER",
  "PRODUCTION_MANAGER",
  "SALES_OFFICER",
];

function formatTimestamp(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

export function AdminUsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null);
  const [isLoadingUsers, setIsLoadingUsers] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isMutating, setIsMutating] = useState(false);

  const [searchDraft, setSearchDraft] = useState("");
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<"ALL" | UserRole>("ALL");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [createRole, setCreateRole] = useState<Exclude<UserRole, "CUSTOMER">>("SALES_OFFICER");
  const [createFields, setCreateFields] = useState<Record<string, string>>({});
  const [createError, setCreateError] = useState<string | null>(null);

  const filters = useMemo(
    () => ({
      ...(search ? { search } : {}),
      ...(roleFilter === "ALL" ? {} : { role: roleFilter }),
      ...(statusFilter === "ALL" ? {} : { active: statusFilter === "ACTIVE" }),
    }),
    [roleFilter, search, statusFilter],
  );

  const loadUsers = useCallback(async () => {
    setIsLoadingUsers(true);
    setListError(null);
    try {
      setUsers(await getAdminUsers(filters));
    } catch (error) {
      setListError(getAdminUserApiError(error, "User accounts could not be loaded.").message);
    } finally {
      setIsLoadingUsers(false);
    }
  }, [filters]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  async function selectUser(userId: number) {
    setIsLoadingDetail(true);
    setDetailError(null);
    setSuccessMessage(null);
    try {
      setSelectedUser(await getAdminUser(userId));
    } catch (error) {
      setDetailError(getAdminUserApiError(error, "User details could not be loaded.").message);
    } finally {
      setIsLoadingDetail(false);
    }
  }

  async function refreshAfterMutation(updatedUser: AdminUser) {
    setSelectedUser(updatedUser);
    await loadUsers();
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError(null);
    setCreateFields({});
    setSuccessMessage(null);
    setIsMutating(true);
    try {
      const response = await createInternalUser({ fullName, email, password, role: createRole });
      setFullName("");
      setEmail("");
      setPassword("");
      setSuccessMessage(response.message);
      await refreshAfterMutation(response.user);
    } catch (error) {
      const apiError = getAdminUserApiError(error, "Internal account could not be created.");
      setCreateError(apiError.message);
      setCreateFields(apiError.fields);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleRoleChange(role: UserRole) {
    if (!selectedUser || role === selectedUser.role) return;
    setDetailError(null);
    setSuccessMessage(null);
    setIsMutating(true);
    try {
      const response = await changeAdminUserRole(selectedUser.id, role);
      setSuccessMessage(response.message);
      await refreshAfterMutation(response.user);
    } catch (error) {
      setDetailError(getAdminUserApiError(error, "Role could not be updated.").message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleStatusChange() {
    if (!selectedUser) return;
    setDetailError(null);
    setSuccessMessage(null);
    setIsMutating(true);
    try {
      const response = await changeAdminUserStatus(selectedUser.id, !selectedUser.active);
      setSuccessMessage(response.message);
      await refreshAfterMutation(response.user);
    } catch (error) {
      setDetailError(getAdminUserApiError(error, "Account status could not be updated.").message);
    } finally {
      setIsMutating(false);
    }
  }

  const isOwnAccount = selectedUser?.id === currentUser?.id;

  return (
    <section className="px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl space-y-6">
        <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Administration</p>
            <h1 className="mt-2 text-3xl font-black text-foreground">User accounts &amp; roles</h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
              Manage the shared authentication accounts. Role and active-state changes are enforced by the API, not by this interface alone.
            </p>
          </div>
          <div className="rounded-2xl border border-border bg-surface/70 px-4 py-3 text-sm text-foreground-muted">
            {users.length} account{users.length === 1 ? "" : "s"} in current view
          </div>
        </div>

        {successMessage ? (
          <div className="rounded-2xl border border-success-border bg-success-soft px-4 py-3 text-sm text-success" role="status">
            {successMessage}
          </div>
        ) : null}

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(22rem,0.9fr)]">
          <div className="space-y-6">
            <div className="rounded-3xl border border-border bg-surface/60 p-5 sm:p-6">
              <form
                className="grid gap-3 sm:grid-cols-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  setSearch(searchDraft.trim());
                }}
              >
                <label className="text-sm font-medium text-foreground-muted">
                  Search
                  <input
                    className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground outline-none focus:border-primary"
                    onChange={(event) => setSearchDraft(event.target.value)}
                    placeholder="Name or email"
                    value={searchDraft}
                  />
                </label>
                <label className="text-sm font-medium text-foreground-muted">
                  Role
                  <select
                    className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground"
                    onChange={(event) => setRoleFilter(event.target.value as "ALL" | UserRole)}
                    value={roleFilter}
                  >
                    <option value="ALL">All roles</option>
                    {allRoles.map((role) => <option key={role} value={role}>{roleLabels[role]}</option>)}
                  </select>
                </label>
                <label className="text-sm font-medium text-foreground-muted">
                  Status
                  <select
                    className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground"
                    onChange={(event) => setStatusFilter(event.target.value as "ALL" | "ACTIVE" | "INACTIVE")}
                    value={statusFilter}
                  >
                    <option value="ALL">All</option>
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                  </select>
                </label>
                <button className="min-h-11 w-full self-end rounded-xl bg-primary px-5 py-2.5 font-bold text-primary-foreground hover:bg-primary-hover" type="submit">
                  Search
                </button>
              </form>
            </div>

            <div className="overflow-hidden rounded-3xl border border-border bg-surface/60">
              <div className="border-b border-border px-5 py-4 sm:px-6">
                <h2 className="font-bold text-foreground">System users</h2>
              </div>
              {listError ? <p className="m-5 rounded-xl bg-danger-soft p-4 text-sm text-danger" role="alert">{listError}</p> : null}
              {isLoadingUsers ? (
                <p className="p-6 text-sm text-muted">Loading user accounts…</p>
              ) : users.length === 0 ? (
                <div className="p-8 text-center">
                  <p className="font-semibold text-foreground">No accounts match these filters.</p>
                  <p className="mt-2 text-sm text-muted">Adjust the search, role, or status filter.</p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-border text-left text-sm">
                    <thead className="bg-background/70 text-xs uppercase tracking-wide text-muted">
                      <tr><th className="px-5 py-3">User</th><th className="px-5 py-3">Role</th><th className="px-5 py-3">Status</th><th className="px-5 py-3"><span className="sr-only">Open</span></th></tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {users.map((account) => (
                        <tr className={selectedUser?.id === account.id ? "bg-primary/5" : ""} key={account.id}>
                          <td className="px-5 py-4"><p className="font-semibold text-foreground">{account.fullName}</p><p className="mt-1 text-xs text-muted">{account.email}</p></td>
                          <td className="px-5 py-4 text-foreground-muted">{roleLabels[account.role]}</td>
                          <td className="px-5 py-4"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${account.active ? "bg-success-soft text-success" : "bg-surface-muted text-muted"}`}>{account.active ? "Active" : "Inactive"}</span></td>
                          <td className="px-5 py-4 text-right"><button className="rounded-lg border border-border px-3 py-2 font-semibold text-foreground hover:border-primary" onClick={() => void selectUser(account.id)} type="button">Manage</button></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>

          <div className="space-y-6">
            <div className="rounded-3xl border border-border bg-surface/60 p-5 sm:p-6">
              <h2 className="text-lg font-bold text-foreground">Account detail</h2>
              {detailError ? <p className="mt-4 rounded-xl bg-danger-soft p-4 text-sm text-danger" role="alert">{detailError}</p> : null}
              {isLoadingDetail ? (
                <p className="mt-5 text-sm text-muted">Loading account detail…</p>
              ) : !selectedUser ? (
                <p className="mt-5 text-sm leading-6 text-muted">Choose an account from the list to inspect its identity, role, and active state.</p>
              ) : (
                <div className="mt-5 space-y-5">
                  <div><p className="text-xs uppercase tracking-wide text-muted">Identity</p><p className="mt-1 font-semibold text-foreground">{selectedUser.fullName}</p><p className="text-sm text-muted">{selectedUser.email}</p><p className="mt-1 text-xs text-muted">User ID {selectedUser.id}</p></div>
                  <label className="block text-sm font-medium text-foreground-muted">Role
                    <select aria-label="Managed user role" className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground disabled:cursor-not-allowed disabled:opacity-60" disabled={isMutating || isOwnAccount} onChange={(event) => void handleRoleChange(event.target.value as UserRole)} value={selectedUser.role}>
                      {allRoles.map((role) => <option key={role} value={role}>{roleLabels[role]}</option>)}
                    </select>
                  </label>
                  {isOwnAccount ? <p className="rounded-xl border border-warning-border bg-warning-soft p-3 text-xs leading-5 text-warning">Your own Administrator role and active access are protected. Another administrator can manage other permitted changes.</p> : null}
                  <button className={`w-full rounded-xl border px-4 py-2.5 font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${selectedUser.active ? "border-danger-border text-danger hover:bg-danger-soft" : "border-success-border text-success hover:bg-success-soft"}`} disabled={isMutating || isOwnAccount} onClick={() => void handleStatusChange()} type="button">{selectedUser.active ? "Deactivate account" : "Activate account"}</button>
                  <dl className="grid grid-cols-2 gap-3 border-t border-border pt-4 text-xs"><div><dt className="text-muted">Created</dt><dd className="mt-1 text-foreground-muted">{formatTimestamp(selectedUser.createdAt)}</dd></div><div><dt className="text-muted">Last updated</dt><dd className="mt-1 text-foreground-muted">{formatTimestamp(selectedUser.updatedAt)}</dd></div></dl>
                </div>
              )}
            </div>

            <div className="rounded-3xl border border-border bg-surface/60 p-5 sm:p-6">
              <h2 className="text-lg font-bold text-foreground">Create internal account</h2>
              <p className="mt-2 text-sm leading-6 text-muted">Customer accounts remain self-service through public registration. This form provisions staff and administrator accounts only.</p>
              {createError ? <p className="mt-4 rounded-xl bg-danger-soft p-3 text-sm text-danger" role="alert">{createError}</p> : null}
              <form className="mt-5 space-y-4" onSubmit={(event) => void handleCreate(event)}>
                <label className="block text-sm font-medium text-foreground-muted">Full name<input className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground" onChange={(event) => setFullName(event.target.value)} required value={fullName}/>{createFields.fullName ? <span className="mt-1 block text-xs text-danger">{createFields.fullName}</span> : null}</label>
                <label className="block text-sm font-medium text-foreground-muted">Email<input className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground" onChange={(event) => setEmail(event.target.value)} required type="email" value={email}/>{createFields.email ? <span className="mt-1 block text-xs text-danger">{createFields.email}</span> : null}</label>
                <label className="block text-sm font-medium text-foreground-muted">Temporary password<input className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground" minLength={8} onChange={(event) => setPassword(event.target.value)} required type="password" value={password}/>{createFields.password ? <span className="mt-1 block text-xs text-danger">{createFields.password}</span> : null}</label>
                <label className="block text-sm font-medium text-foreground-muted">Internal role<select className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground" onChange={(event) => setCreateRole(event.target.value as Exclude<UserRole, "CUSTOMER">)} value={createRole}>{internalRoles.map((role) => <option key={role} value={role}>{roleLabels[role]}</option>)}</select>{createFields.role ? <span className="mt-1 block text-xs text-danger">{createFields.role}</span> : null}</label>
                <button className="w-full rounded-xl bg-primary px-4 py-3 font-bold text-primary-foreground hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60" disabled={isMutating} type="submit">{isMutating ? "Saving…" : "Create internal account"}</button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
