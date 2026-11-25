"use client";

import { useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import {
  IconActivityHeartbeat,
  IconKey,
  IconLogout,
  IconPlugConnected,
  IconRocket,
  IconSend,
  IconStack2,
} from "@tabler/icons-react";
import { Sidebar, SidebarBody, SidebarLink } from "@/components/ui/sidebar";
import { session } from "@/lib/session";
import { useApi } from "@/lib/api";
import type { Tenant } from "@/lib/types";
import { Logo } from "./logo";

const NAV = [
  { label: "Overview", href: "/overview", icon: IconActivityHeartbeat },
  { label: "Endpoints", href: "/endpoints", icon: IconPlugConnected },
  { label: "Events", href: "/events", icon: IconStack2 },
  { label: "Deliveries", href: "/deliveries", icon: IconSend },
  { label: "Playground", href: "/playground", icon: IconRocket },
  { label: "API keys", href: "/api-keys", icon: IconKey },
];

export function AppShell({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const router = useRouter();
  const { data: tenant } = useApi<Tenant>("/v1/me");

  return (
    <div className="flex h-screen w-full flex-col overflow-hidden md:flex-row">
      <Sidebar open={open} setOpen={setOpen}>
        <SidebarBody className="justify-between gap-10 border-r border-sidebar-border bg-sidebar">
          <div className="flex flex-1 flex-col overflow-x-hidden overflow-y-auto">
            <Logo withWordmark={open} className="py-1" />
            <nav className="mt-8 flex flex-col gap-1">
              {NAV.map(({ label, href, icon: Icon }) => {
                const active = pathname.startsWith(href);
                return (
                  <SidebarLink
                    key={href}
                    active={active}
                    className={active ? "rounded-md bg-sidebar-accent px-1.5 text-sidebar-foreground" : "px-1.5"}
                    link={{
                      label,
                      href,
                      icon: <Icon className={active ? "size-5 shrink-0 text-primary" : "size-5 shrink-0 text-muted-foreground"} />,
                    }}
                  />
                );
              })}
            </nav>
          </div>
          <div className="flex flex-col gap-2 border-t border-sidebar-border pt-3">
            <div className="flex items-center gap-2 px-1.5 py-1">
              <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-secondary text-[11px] font-semibold uppercase">
                {tenant?.name?.[0] ?? "·"}
              </span>
              {open && (
                <span className="min-w-0">
                  <span className="block truncate text-sm">{tenant?.name ?? "…"}</span>
                  <span className="block truncate font-mono text-[11px] text-muted-foreground">{tenant?.slug}</span>
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={() => {
                session.clear();
                router.replace("/login");
              }}
              className="flex items-center gap-2 px-1.5 py-2 text-sm text-muted-foreground hover:text-foreground"
            >
              <IconLogout className="size-5 shrink-0" />
              {open && "Sign out"}
            </button>
          </div>
        </SidebarBody>
      </Sidebar>
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-8 px-6 py-8 md:px-10">{children}</div>
      </main>
    </div>
  );
}
