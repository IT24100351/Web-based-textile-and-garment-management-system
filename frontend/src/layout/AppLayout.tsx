import {
  Bell,
  Boxes,
  ClipboardList,
  Factory,
  FileCheck2,
  Gauge,
  House,
  LayoutGrid,
  LogIn,
  LogOut,
  Menu,
  Package,
  Search,
  Shirt,
  Truck,
  UserRound,
  Users,
  Warehouse,
  X,
  type LucideIcon,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";

import {
  getNotifications,
  NOTIFICATIONS_CHANGED_EVENT,
} from "../api/notifications";
import { useAuth } from "../auth/useAuth";
import { BrandIdentity } from "../components/BrandLogo";
import { WorkspaceBackground } from "../components/WorkspaceBackground";
import { Button } from "../components/ui/Button";
import { buttonStyles } from "../components/ui/buttonStyles";
import { ThemeToggle } from "../components/ui/ThemeToggle";
import { cn } from "../components/ui/cn";
import { MotionPage } from "../motion/MotionPage";
import { usePresence } from "../motion/usePresence";
import { getNavigationItems, roleLabels, type NavigationItem } from "../navigation/navigation";

type NavigationGroup = "Overview" | "Operations" | "Management";

function navigationGroup(item: NavigationItem): NavigationGroup {
  const path = item.path;
  if (path === "/" || path.startsWith("/dashboard")) return "Overview";
  if (
    path.startsWith("/products")
    || path.startsWith("/orders")
    || path.startsWith("/quotations")
    || path.startsWith("/supplies")
    || path.startsWith("/supplier")
    || path.startsWith("/inventory")
    || path.startsWith("/production")
    || path.startsWith("/deliveries")
  ) return "Operations";
  return "Management";
}

function navigationIcon(item: NavigationItem): LucideIcon {
  const path = item.path;
  if (path === "/") return House;
  if (path.startsWith("/dashboard")) return Gauge;
  if (path.startsWith("/products/new")) return Package;
  if (path.startsWith("/products")) return Shirt;
  if (path.startsWith("/orders")) return ClipboardList;
  if (path.startsWith("/quotations")) return FileCheck2;
  if (path.startsWith("/inventory")) return Warehouse;
  if (path.startsWith("/supplies") || path.startsWith("/supplier")) return Boxes;
  if (path.startsWith("/production")) return Factory;
  if (path.startsWith("/deliveries")) return Truck;
  if (path.startsWith("/notifications")) return Bell;
  if (path.startsWith("/search")) return Search;
  if (path.startsWith("/admin")) return Users;
  if (path.startsWith("/profile")) return UserRound;
  if (path.startsWith("/login")) return LogIn;
  return LayoutGrid;
}

function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <Link aria-label="LankaWear Apparel home" className="min-w-0" to="/">
      <BrandIdentity compact={compact} responsive />
    </Link>
  );
}

function NotificationBellLink({ refreshKey }: { refreshKey: string }) {
  const [unreadCount, setUnreadCount] = useState(0);

  const refreshUnreadCount = useCallback((signal?: AbortSignal) => {
    void getNotifications(true, signal)
      .then((inbox) => {
        if (!signal?.aborted) {
          setUnreadCount(Math.max(0, inbox.unreadCount));
        }
      })
      .catch(() => {
        // Keep the workspace usable when notification refresh is temporarily unavailable.
      });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    refreshUnreadCount(controller.signal);

    const handleNotificationsChanged = () => refreshUnreadCount();
    const handleFocus = () => refreshUnreadCount();
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        refreshUnreadCount();
      }
    };
    const intervalId = window.setInterval(refreshUnreadCount, 60_000);

    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, handleNotificationsChanged);
    window.addEventListener("focus", handleFocus);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      controller.abort();
      window.clearInterval(intervalId);
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, handleNotificationsChanged);
      window.removeEventListener("focus", handleFocus);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [refreshKey, refreshUnreadCount]);

  const badgeText = unreadCount > 99 ? "99+" : String(unreadCount);
  const notificationLabel = unreadCount === 0
    ? "Notifications, no unread notifications"
    : `Notifications, ${unreadCount} unread`;

  return (
    <Link
      aria-label={notificationLabel}
      className={cn(buttonStyles({ size: "icon", variant: "ghost" }), "relative overflow-visible")}
      to="/notifications"
    >
      <Bell aria-hidden="true" className="h-[1.15rem] w-[1.15rem]" />
      {unreadCount > 0 ? (
        <span
          aria-hidden="true"
          className="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full border-2 border-background bg-danger px-1 text-[0.625rem] font-extrabold leading-none text-white shadow-sm"
        >
          {badgeText}
        </span>
      ) : null}
    </Link>
  );
}

function RouteCanvas({ pathname }: { pathname: string }) {
  const page = <MotionPage key={pathname}><Outlet /></MotionPage>;

  if (pathname === "/") {
    return page;
  }

  return (
    <WorkspaceBackground className="min-h-[calc(100svh-4.5rem)]">
      {page}
    </WorkspaceBackground>
  );
}

function NavigationLinks({
  items,
  onNavigate,
}: {
  items: NavigationItem[];
  onNavigate?: () => void;
}) {
  const groups: NavigationGroup[] = ["Overview", "Operations", "Management"];

  return (
    <nav aria-label="Primary navigation" className="space-y-6">
      {groups.map((group) => {
        const groupedItems = items.filter((item) => navigationGroup(item) === group);
        if (groupedItems.length === 0) return null;
        return (
          <section key={group}>
            <h2 className="mb-2 px-3 text-[0.68rem] font-bold uppercase tracking-[0.16em] text-muted">{group}</h2>
            <div className="space-y-1">
              {groupedItems.map((item) => {
                const Icon = navigationIcon(item);
                return (
                  <NavLink
                    className={({ isActive }) => cn(
                      "relative flex min-h-11 items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition",
                      isActive
                        ? "bg-primary-soft text-primary"
                        : "text-foreground-muted hover:bg-surface-muted hover:text-foreground",
                    )}
                    end={item.path === "/"}
                    key={item.path}
                    onClick={onNavigate}
                    title={item.description}
                    to={item.path}
                  >
                    {({ isActive }) => (
                      <>
                        <span className={cn(
                          "absolute inset-y-2 left-0 w-0.5 origin-center scale-y-50 rounded-full bg-primary opacity-0 transition duration-[var(--motion-normal)] ease-[var(--ease-emphasized)]",
                          isActive && "scale-y-100 opacity-100",
                        )} />
                        <Icon aria-hidden="true" className="h-[1.1rem] w-[1.1rem] shrink-0" />
                        <span>{item.label}</span>
                      </>
                    )}
                  </NavLink>
                );
              })}
            </div>
          </section>
        );
      })}
    </nav>
  );
}

function PublicShell() {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuPresence = usePresence(isMenuOpen);
  const items = getNavigationItems(user);

  useEffect(() => {
    setIsMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!isMenuOpen || typeof window.matchMedia !== "function") return undefined;
    const desktopMedia = window.matchMedia("(min-width: 768px)");
    const closeOnDesktop = (event: MediaQueryListEvent) => {
      if (event.matches) setIsMenuOpen(false);
    };
    if (desktopMedia.matches) setIsMenuOpen(false);
    desktopMedia.addEventListener("change", closeOnDesktop);
    return () => desktopMedia.removeEventListener("change", closeOnDesktop);
  }, [isMenuOpen]);

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-40 border-b border-border bg-background/90 backdrop-blur-xl">
        <div className="mx-auto flex min-h-18 max-w-[1440px] items-center justify-between gap-4 px-4 sm:px-6 lg:px-10">
          <Logo />
          <nav aria-label={isLoading ? "Public navigation loading" : "Primary navigation"} className="hidden items-center gap-1 md:flex">
            {items.filter((item) => item.path !== "/login").slice(0, 4).map((item) => (
              <NavLink
                aria-label={item.path === "/login" ? "Sign in navigation" : undefined}
                className={({ isActive }) => cn(
                  "rounded-lg px-3 py-2 text-sm font-semibold transition",
                  isActive ? "bg-primary-soft text-primary" : "text-foreground-muted hover:text-foreground",
                )}
                end={item.path === "/"}
                key={item.path}
                to={item.path}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <ThemeToggle compact />
            {!user ? (
              <span className="hidden sm:block">
                <Link aria-label="Sign in to LankaWear Apparel" className={buttonStyles({ size: "sm", variant: "primary" })} to="/login">Sign in</Link>
              </span>
            ) : null}
            <Button
              aria-controls="public-mobile-navigation"
              aria-expanded={isMenuOpen}
              aria-label="Toggle navigation"
              className="md:hidden"
              onClick={() => setIsMenuOpen((open) => !open)}
              size="icon"
              variant="secondary"
            >
              {isMenuOpen ? <X aria-hidden="true" className="h-5 w-5" /> : <Menu aria-hidden="true" className="h-5 w-5" />}
            </Button>
          </div>
        </div>
        {menuPresence.isMounted ? (
          <nav
            aria-label="Mobile navigation"
            className="motion-dropdown border-t border-border bg-surface px-4 py-3 md:hidden"
            data-motion-state={menuPresence.motionState}
            id="public-mobile-navigation"
            inert={menuPresence.motionState === "closed"}
            onTransitionEnd={menuPresence.onTransitionEnd}
          >
            <div className="mx-auto grid max-w-[1440px] gap-1">
              {items.map((item) => (
                <NavLink className="rounded-xl px-4 py-3 text-sm font-semibold text-foreground-muted hover:bg-surface-muted hover:text-foreground" key={item.path} onClick={() => setIsMenuOpen(false)} to={item.path}>{item.label}</NavLink>
              ))}
            </div>
          </nav>
        ) : null}
      </header>
      <main><RouteCanvas pathname={location.pathname} /></main>
    </div>
  );
}

export function AppLayout() {
  const { user, isLoading, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [isNavigationOpen, setIsNavigationOpen] = useState(false);
  const navigationPresence = usePresence(isNavigationOpen);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const navigationItems = getNavigationItems(user);

  useEffect(() => {
    setIsNavigationOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!isNavigationOpen || typeof window.matchMedia !== "function") return undefined;
    const desktopMedia = window.matchMedia("(min-width: 1024px)");
    const closeOnDesktop = (event: MediaQueryListEvent) => {
      if (event.matches) setIsNavigationOpen(false);
    };
    if (desktopMedia.matches) setIsNavigationOpen(false);
    desktopMedia.addEventListener("change", closeOnDesktop);
    return () => desktopMedia.removeEventListener("change", closeOnDesktop);
  }, [isNavigationOpen]);

  useEffect(() => {
    if (!navigationPresence.isMounted) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [navigationPresence.isMounted]);

  if (isLoading || !user) return <PublicShell />;

  const currentItem = navigationItems
    .filter((item) => item.path === "/" ? location.pathname === "/" : location.pathname.startsWith(item.path))
    .sort((left, right) => right.path.length - left.path.length)[0];

  async function handleLogout() {
    setLogoutError(null);
    setIsLoggingOut(true);
    try {
      await logout();
      navigate("/", { replace: true });
    } catch {
      setLogoutError("Sign out failed. Your session may still be active; please try again.");
    } finally {
      setIsLoggingOut(false);
    }
  }

  const initials = user?.fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "TG";

  return (
    <div className="min-h-screen bg-background text-foreground">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-68 border-r border-border bg-surface/95 px-4 py-5 backdrop-blur-xl lg:flex lg:flex-col">
        <div className="px-2"><Logo /></div>
        <div className="scrollbar-subtle mt-8 flex-1 overflow-y-auto pr-1">
          <NavigationLinks items={navigationItems} />
        </div>
        <div className="mt-5 rounded-2xl border border-border bg-surface-muted p-3">
          <div className="flex items-center gap-3">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-primary-soft text-sm font-bold text-primary">{initials}</span>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-foreground">{user?.fullName ?? "Checking session…"}</p>
              <p className="truncate text-xs text-muted">{user ? roleLabels[user.role] : "Secure workspace"}</p>
            </div>
          </div>
          <p className="sr-only">Navigation shows only the modules and supporting tools available to your current role.</p>
          <p className="sr-only">Role-aware workspace</p>
        </div>
      </aside>

      {navigationPresence.isMounted ? (
        <div
          className="motion-drawer-overlay fixed inset-0 z-50 lg:hidden"
          data-motion-state={navigationPresence.motionState}
          inert={navigationPresence.motionState === "closed"}
          onTransitionEnd={navigationPresence.onTransitionEnd}
        >
          <button
            aria-label="Close navigation"
            className="absolute inset-0 bg-black/50"
            onClick={() => setIsNavigationOpen(false)}
            type="button"
          />
          <aside className="motion-drawer absolute inset-y-0 left-0 flex w-[min(88vw,20rem)] flex-col border-r border-border bg-surface p-5 shadow-2xl" id="primary-navigation">
            <div className="flex items-center justify-between gap-3">
              <Logo />
              <Button aria-label="Close navigation" onClick={() => setIsNavigationOpen(false)} size="icon" variant="ghost">
                <X aria-hidden="true" className="h-5 w-5" />
              </Button>
            </div>
            <div className="scrollbar-subtle mt-8 overflow-y-auto">
              <NavigationLinks items={navigationItems} onNavigate={() => setIsNavigationOpen(false)} />
            </div>
          </aside>
        </div>
      ) : null}

      <div className="lg:pl-68">
        <header className="sticky top-0 z-30 border-b border-border bg-background/90 backdrop-blur-xl">
          <div className="flex min-h-18 items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
            <div className="flex min-w-0 items-center gap-3">
              <Button
                aria-controls="primary-navigation"
                aria-expanded={isNavigationOpen}
                aria-label="Toggle navigation"
                className="lg:hidden"
                onClick={() => setIsNavigationOpen(true)}
                size="icon"
                variant="secondary"
              >
                <Menu aria-hidden="true" className="h-5 w-5" />
              </Button>
              <div className="min-w-0">
                <p className="text-xs font-medium text-muted">Workspace</p>
                <p className="truncate text-sm font-bold text-foreground sm:text-base">{currentItem?.label ?? "LankaWear operations"}</p>
              </div>
            </div>

            <div className="flex items-center gap-1.5 sm:gap-2">
              <Link aria-label="Search" className={buttonStyles({ size: "icon", variant: "ghost" })} to="/search">
                <Search aria-hidden="true" className="h-[1.15rem] w-[1.15rem]" />
              </Link>
              <NotificationBellLink refreshKey={location.pathname} />
              <ThemeToggle compact />
              <Button
                aria-label="Sign out"
                isLoading={isLoggingOut}
                onClick={handleLogout}
                size="icon"
                variant="ghost"
              >
                <LogOut aria-hidden="true" className="h-[1.15rem] w-[1.15rem]" />
              </Button>
            </div>
          </div>
          {logoutError ? (
            <p className="border-t border-danger-border bg-danger-soft px-6 py-2 text-sm text-danger" role="alert">{logoutError}</p>
          ) : null}
        </header>

        <main className="min-w-0"><RouteCanvas pathname={location.pathname} /></main>
      </div>
    </div>
  );
}
