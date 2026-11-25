import { cn } from "@/lib/utils";
import type { DeliveryStatus } from "@/lib/types";

const STYLES: Record<string, string> = {
  SUCCEEDED: "bg-success/12 text-success ring-success/25",
  DEAD: "bg-destructive/12 text-destructive ring-destructive/25",
  FAILED: "bg-destructive/12 text-destructive ring-destructive/25",
  RETRYING: "bg-warning/12 text-warning ring-warning/25",
  PENDING: "bg-warning/12 text-warning ring-warning/25",
  QUEUED: "bg-primary/10 text-primary ring-primary/25",
  DELIVERING: "bg-primary/10 text-primary ring-primary/25",
  ENABLED: "bg-success/12 text-success ring-success/25",
  DISABLED: "bg-muted text-muted-foreground ring-border",
};

const DOT: Record<string, string> = {
  SUCCEEDED: "bg-success",
  ENABLED: "bg-success",
  DEAD: "bg-destructive",
  FAILED: "bg-destructive",
  RETRYING: "bg-warning",
  PENDING: "bg-warning",
  QUEUED: "bg-primary animate-pulse",
  DELIVERING: "bg-primary animate-pulse",
  DISABLED: "bg-muted-foreground",
};

export function StatusBadge({ status, className }: { status: DeliveryStatus | string; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium tracking-wide ring-1 ring-inset",
        STYLES[status] ?? STYLES.DISABLED,
        className,
      )}
    >
      <span className={cn("size-1.5 rounded-full", DOT[status] ?? DOT.DISABLED)} />
      {status.toLowerCase()}
    </span>
  );
}

export function HttpStatus({ code }: { code: number | null }) {
  if (code == null) return <span className="text-muted-foreground">—</span>;
  const tone = code < 300 ? "text-success" : code < 500 && code !== 429 && code !== 408 ? "text-destructive" : "text-warning";
  return <span className={cn("font-mono text-xs", tone)}>{code}</span>;
}
