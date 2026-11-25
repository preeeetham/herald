import { cn } from "@/lib/utils";

export function Logo({ withWordmark = true, className }: { withWordmark?: boolean; className?: string }) {
  return (
    <span className={cn("flex items-center gap-2", className)}>
      <span className="relative flex size-7 shrink-0 items-center justify-center rounded-lg bg-primary/15 ring-1 ring-primary/30">
        <svg viewBox="0 0 24 24" className="size-4 text-primary" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
          <path d="M4 12h3l2-6 4 12 2-6h5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
      {withWordmark && <span className="text-sm font-semibold tracking-tight">Herald</span>}
    </span>
  );
}
