import axios from "axios";
import {
  ArrowRight,
  Boxes,
  CheckCircle2,
  Factory,
  Layers3,
  LockKeyhole,
  PackageCheck,
  ScanSearch,
  Shirt,
  ShoppingBag,
  Truck,
  UsersRound,
  Warehouse,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { useAuth } from "../auth/useAuth";
import { BrandIdentity } from "../components/BrandLogo";
import { HomeLowerVideo } from "../components/HomeLowerVideo";
import { Badge } from "../components/ui/Badge";
import { HeroActionLink } from "../components/ui/HeroActionLink";
import { Reveal } from "../motion/Reveal";
import { dashboardPagePaths, roleLabels } from "../navigation/navigation";

type ApiState = "checking" | "available" | "unavailable";

const modules: Array<{ title: string; description: string; Icon: LucideIcon }> = [
  { title: "Garment products", description: "Catalog garment categories, variants, sizes, colors and current pricing.", Icon: Shirt },
  { title: "Supplier management", description: "Maintain supplier profiles and the raw materials they can provide.", Icon: UsersRound },
  { title: "Fabric inventory", description: "Monitor material quantities, availability and persisted low-stock thresholds.", Icon: Warehouse },
  { title: "Order management", description: "Coordinate customer orders, quotations, invoices and payment records.", Icon: ShoppingBag },
  { title: "Production management", description: "Plan garment production, material requirements, usage and quality control.", Icon: Factory },
  { title: "Delivery management", description: "Schedule eligible completed orders and track delivery progress.", Icon: Truck },
];

const benefits: Array<{ title: string; description: string; Icon: LucideIcon }> = [
  { title: "Connected operations", description: "Core modules share approved records across one operational workflow.", Icon: Layers3 },
  { title: "Role-based access", description: "Navigation and server authorization respect each authenticated account role.", Icon: LockKeyhole },
  { title: "Operational visibility", description: "Dashboards and search surface only real, permitted application data.", Icon: ScanSearch },
  { title: "Controlled handoffs", description: "Orders, materials, production and delivery retain their business safeguards.", Icon: PackageCheck },
];

const workflow = [
  { label: "Supplier", caption: "Raw material source", Icon: Boxes },
  { label: "Inventory", caption: "Material availability", Icon: Warehouse },
  { label: "Production", caption: "Garment manufacturing", Icon: Factory },
  { label: "Delivery", caption: "Customer fulfilment", Icon: Truck },
];

export function PublicHomePage() {
  const [apiState, setApiState] = useState<ApiState>("checking");
  const { user, sessionError } = useAuth();

  useEffect(() => {
    const controller = new AbortController();
    axios
      .get("/api/health", { signal: controller.signal })
      .then(() => setApiState("available"))
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setApiState("unavailable");
      });
    return () => controller.abort();
  }, []);

  const statusText = {
    checking: "Checking system",
    available: "System operational",
    unavailable: "System unavailable",
  }[apiState];

  return (
    <div className="overflow-x-clip">
      <section className="home-hero relative isolate overflow-hidden border-b border-border">
        <div aria-hidden="true" className="home-hero-media home-hero-media-light" />
        <div aria-hidden="true" className="home-hero-media home-hero-media-dark" />
        <div className="mx-auto flex min-h-[calc(100svh-4.5rem)] max-w-[1440px] items-center px-5 py-20 sm:px-8 lg:min-h-[46rem] lg:px-12 lg:py-28">
          <div className="motion-hero-sequence max-w-3xl">
            <Badge tone="primary"><span className="h-1.5 w-1.5 rounded-full bg-primary" />LankaWear Apparel · Smarter textile operations</Badge>
            <h1 className="mt-6 max-w-3xl text-4xl font-bold leading-[1.06] tracking-[-0.04em] text-foreground sm:text-5xl lg:text-7xl">
              Manage every stage of garment production from one connected platform.
            </h1>
            <p className="mt-6 max-w-xl text-base leading-7 text-foreground-muted sm:text-lg sm:leading-8">
              Coordinate suppliers, materials, garment products, customer orders, production and delivery through one centralized management system.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              {user ? (
                <HeroActionLink to={dashboardPagePaths[user.role]}>
                  Open {roleLabels[user.role].toLowerCase()} dashboard <ArrowRight aria-hidden="true" className="h-4 w-4" />
                </HeroActionLink>
              ) : (
                <HeroActionLink to="/register">
                  Get started <ArrowRight aria-hidden="true" className="h-4 w-4" />
                </HeroActionLink>
              )}
              <HeroActionLink to="/products" tone="secondary">Explore products</HeroActionLink>
              {!user ? <HeroActionLink to="/login" tone="quiet">Sign in</HeroActionLink> : null}
            </div>
            <div className="mt-8 flex flex-wrap items-center gap-x-6 gap-y-3 text-sm text-foreground-muted">
              <span className="inline-flex items-center gap-2"><CheckCircle2 aria-hidden="true" className="h-4 w-4 text-success" />Role-aware workspaces</span>
              <span className="inline-flex items-center gap-2"><CheckCircle2 aria-hidden="true" className="h-4 w-4 text-success" />Connected module records</span>
              <span className="inline-flex items-center gap-2 rounded-full border border-border bg-surface/85 px-3 py-1.5 backdrop-blur" aria-live="polite">
                <span aria-hidden="true" className={`h-2 w-2 rounded-full ${apiState === "available" ? "bg-success" : apiState === "unavailable" ? "bg-danger" : "animate-pulse bg-warning motion-reduce:animate-none"}`} />
                {statusText}
                <span className="sr-only">{apiState === "available" ? "API connection available" : apiState === "unavailable" ? "API connection unavailable" : "Checking API connection…"}</span>
              </span>
            </div>
            {sessionError ? <p className="mt-6 max-w-xl rounded-xl border border-warning-border bg-warning-soft px-4 py-3 text-sm text-warning" role="alert">{sessionError}</p> : null}
          </div>
        </div>
      </section>

      <div className="home-lower-media-region relative isolate">
        <HomeLowerVideo />
        <div className="relative z-10">
      <Reveal>
      <section className="mx-auto max-w-[1440px] px-5 py-20 sm:px-8 lg:px-12" id="modules">
        <div className="home-lower-glass max-w-3xl rounded-3xl border border-border p-7 sm:p-8">
          <p className="text-sm font-semibold text-primary">One operational system</p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Purpose-built modules for the garment lifecycle</h2>
          <p className="mt-4 text-base leading-7 text-foreground-muted">Each workspace remains focused on its existing responsibilities while approved records move safely between modules.</p>
        </div>
        <div className="motion-stagger mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {modules.map(({ Icon, description, title }) => (
            <article className="home-lower-glass motion-interactive-card group rounded-2xl border border-border p-6 hover:border-primary/40" key={title}>
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-primary-soft text-primary transition group-hover:bg-primary group-hover:text-primary-foreground"><Icon aria-hidden="true" className="h-6 w-6" /></span>
              <h3 className="mt-5 text-lg font-bold text-foreground">{title}</h3>
              <p className="mt-2 text-sm leading-6 text-foreground-muted">{description}</p>
            </article>
          ))}
        </div>
      </section>
      </Reveal>

      <Reveal>
      <section className="home-lower-band border-y border-border py-20" id="workflow">
        <div className="mx-auto max-w-[1440px] px-5 sm:px-8 lg:px-12">
          <div className="home-lower-glass mx-auto max-w-3xl rounded-3xl border border-border p-7 text-center sm:p-8">
            <p className="text-sm font-semibold text-primary">From material to fulfilment</p>
            <h2 className="mt-2 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">A connected, traceable workflow</h2>
          </div>
          <div className="motion-stagger relative mt-12 grid gap-4 lg:grid-cols-4">
            <div className="absolute left-[12.5%] right-[12.5%] top-8 hidden h-px bg-border-strong lg:block" />
            {workflow.map(({ Icon, caption, label }, index) => (
              <article className="home-lower-glass relative z-10 flex items-center gap-4 rounded-2xl border border-border p-5 lg:block lg:text-center" key={label}>
                <span className="mx-auto grid h-16 w-16 shrink-0 place-items-center rounded-2xl border border-primary/20 bg-primary-soft text-primary"><Icon aria-hidden="true" className="h-7 w-7" /></span>
                <div><p className="mt-0 text-xs font-bold text-primary lg:mt-4">0{index + 1}</p><h3 className="mt-1 text-lg font-bold text-foreground">{label}</h3><p className="mt-1 text-sm text-muted">{caption}</p></div>
              </article>
            ))}
          </div>
          <div className="mx-auto mt-5 flex max-w-2xl items-center justify-center gap-3 rounded-2xl border border-info-border bg-info-soft px-5 py-4 text-center text-sm font-medium text-info">
            <Shirt aria-hidden="true" className="h-5 w-5 shrink-0" />Garment products and customer orders connect with production before delivery.
          </div>
        </div>
      </section>
      </Reveal>

      <Reveal>
      <section className="border-b border-border" id="benefits">
        <div className="mx-auto max-w-[1440px] px-5 py-24 sm:px-8 lg:px-12 lg:py-28">
          <div className="grid gap-6 lg:grid-cols-[.75fr_1.25fr] lg:items-start">
            <div className="home-lower-glass rounded-3xl border border-border p-7 sm:p-8">
              <p className="text-sm font-semibold text-primary">Designed for control</p>
              <h2 className="mt-2 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Clear information for every permitted role.</h2>
              <p className="mt-4 text-base leading-7 text-foreground-muted">LankaWear Apparel brings operational records into focused workspaces without changing ownership, permissions, or module safeguards.</p>
            </div>
            <div className="motion-stagger grid gap-4 sm:grid-cols-2">
              {benefits.map(({ Icon, description, title }) => (
                <article className="home-lower-glass rounded-2xl border border-border p-5" key={title}><Icon aria-hidden="true" className="h-5 w-5 text-primary" /><h3 className="mt-4 font-bold text-foreground">{title}</h3><p className="mt-2 text-sm leading-6 text-foreground-muted">{description}</p></article>
              ))}
            </div>
          </div>
        </div>
      </section>
      </Reveal>
        </div>
      </div>

      <Reveal>
      <footer className="border-t border-border bg-surface">
        <div className="mx-auto grid max-w-[1440px] gap-10 px-5 py-12 sm:px-8 md:grid-cols-[1.5fr_1fr_1fr] lg:px-12">
          <div><BrandIdentity /><p className="mt-5 max-w-md text-sm leading-6 text-foreground-muted">A connected workspace for garment products, suppliers, inventory, customer orders, production and delivery.</p></div>
          <div><h2 className="text-sm font-bold text-foreground">Modules</h2><div className="mt-4 grid gap-2 text-sm text-foreground-muted"><Link className="hover:text-primary" to="/products">Products</Link><span>Orders</span><span>Inventory</span><span>Production</span><span>Delivery</span></div></div>
          <div><h2 className="text-sm font-bold text-foreground">System</h2><div className="mt-4 grid gap-2 text-sm text-foreground-muted"><Link aria-label="Sign in to LankaWear Apparel" className="hover:text-primary" to="/login">Sign in</Link><Link className="hover:text-primary" to="/register">Register</Link></div><p className="mt-6 text-xs leading-5 text-muted">SE2030 Software Engineering<br />Sri Lanka Institute of Information Technology</p></div>
        </div>
      </footer>
      </Reveal>
    </div>
  );
}
