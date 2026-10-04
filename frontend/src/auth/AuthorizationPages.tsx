import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";

import { getApiErrorMessage, type UserRole } from "../api/auth";
import {
  getRoleAccess,
  isCanceledRequest,
  type InternalRole,
  type RoleAccessResponse,
} from "../api/roleAccess";
import { ErrorState, LoadingState } from "../components/AppStates";
import { WorkspaceBackground } from "../components/WorkspaceBackground";
import { useAuth } from "./useAuth";

function StatePage({
  eyebrow,
  title,
  message,
  action,
}: {
  eyebrow: string;
  title: string;
  message: string;
  action?: React.ReactNode;
}) {
  return (
    <WorkspaceBackground className="min-h-screen">
      <main className="flex min-h-screen items-center justify-center px-6 py-12 text-foreground">
        <section className="w-full max-w-xl rounded-3xl border border-border bg-surface/95 p-8 shadow-2xl shadow-black/30 backdrop-blur-md sm:p-12">
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary">
            {eyebrow}
          </p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight">{title}</h1>
          <p className="mt-5 leading-7 text-foreground-muted">{message}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            {action}
            <Link
              className="rounded-full border border-border px-5 py-2 text-sm font-semibold hover:border-primary"
              to="/"
            >
              Public home
            </Link>
          </div>
        </section>
      </main>
    </WorkspaceBackground>
  );
}

export function ForbiddenPage() {
  return (
    <StatePage
      eyebrow="403 · Forbidden"
      message="Your account is authenticated, but its role does not permit this function."
      title="Access forbidden"
    />
  );
}

export function ProtectedRoute({
  allowedRoles,
  children,
}: {
  allowedRoles: readonly UserRole[];
  children: React.ReactNode;
}) {
  const { user, isLoading, sessionError } = useAuth();

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl p-6 sm:p-10">
        <LoadingState
          message="Your authenticated session is being checked."
          title="Checking access…"
        />
      </div>
    );
  }

  if (sessionError && !user) {
    return (
      <div className="mx-auto max-w-3xl p-6 sm:p-10">
        <ErrorState
          message="Access could not be verified safely. Return to the public page and try again."
          title="Unable to verify access"
        />
      </div>
    );
  }

  if (!user) {
    return <Navigate replace to="/" />;
  }

  if (!allowedRoles.includes(user.role)) {
    return <Navigate replace to="/forbidden" />;
  }

  return children;
}

type AccessState =
  | { status: "loading" }
  | { status: "success"; response: RoleAccessResponse }
  | { status: "error"; message: string };

export function RoleAccessPage({ role }: { role: InternalRole }) {
  const [state, setState] = useState<AccessState>({ status: "loading" });
  const roleLabel = role.replaceAll("_", " ");

  useEffect(() => {
    const controller = new AbortController();

    void getRoleAccess(role, controller.signal)
      .then((response) => setState({ status: "success", response }))
      .catch((error: unknown) => {
        if (!isCanceledRequest(error)) {
          setState({
            status: "error",
            message: getApiErrorMessage(
              error,
              "The server could not confirm access. Please try again.",
            ),
          });
        }
      });

    return () => controller.abort();
  }, [role]);

  return (
    <div className="mx-auto max-w-4xl p-6 sm:p-10 lg:p-12">
      <section className="rounded-3xl border border-border bg-surface p-8 shadow-2xl shadow-black/20 sm:p-10">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary">
          Protected role function
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight">{roleLabel}</h1>
        {state.status === "loading" ? (
          <div className="mt-6">
            <LoadingState
              message="Spring Security is checking the authenticated role."
              title="Confirming server authorization…"
            />
          </div>
        ) : state.status === "success" ? (
          <div className="mt-6 rounded-2xl bg-success-soft p-5 text-success">
            <p className="font-semibold">{state.response.message}</p>
            <p className="mt-2 text-sm text-success">
              The server authorized {state.response.role.replaceAll("_", " ")}.
            </p>
          </div>
        ) : (
          <div className="mt-6">
            <ErrorState message={state.message} title="Authorization check failed" />
          </div>
        )}
        <p className="mt-6 text-sm leading-6 text-muted">
          This page verifies the shared authorization boundary only. Business-module
          actions will be added by their approved tickets.
        </p>
        <Link
          className="mt-8 inline-block rounded-full border border-border px-5 py-2 text-sm font-semibold hover:border-primary"
          to="/"
        >
          Return home
        </Link>
      </section>
    </div>
  );
}
