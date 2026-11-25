import type { Metadata } from "next";
import { Suspense } from "react";
import { BackgroundRippleEffect } from "@/components/ui/background-ripple-effect";
import { Logo } from "@/components/herald/logo";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return (
    <div className="relative flex min-h-screen w-full flex-col items-center justify-center overflow-hidden">
      <BackgroundRippleEffect rows={12} cols={30} cellSize={56} />
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_center,transparent_10%,var(--background)_70%)]" />
      <div className="relative z-10 w-full max-w-md px-6">
        <Logo className="mb-8 justify-center [&>span:last-child]:text-lg" />
        <div className="rounded-2xl border border-border bg-card/80 p-8 shadow-2xl shadow-black/40 backdrop-blur">
          <h1 className="text-xl font-semibold tracking-tight">Sign in to your console</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Use a tenant API key. Keys are issued when a tenant is onboarded or from the API keys page.
          </p>
          <Suspense>
            <LoginForm />
          </Suspense>
        </div>
        <p className="mt-6 text-center text-xs text-muted-foreground">
          Publish once. Delivered, signed and retried — for every partner.
        </p>
      </div>
    </div>
  );
}
