import { HttpStatus, StatusBadge } from "./status-badge";
import { dateTime, ms } from "@/lib/format";
import type { DeliveryAttempt } from "@/lib/types";
import { cn } from "@/lib/utils";

const RAIL: Record<DeliveryAttempt["outcome"], string> = {
  SUCCEEDED: "bg-success shadow-[0_0_12px] shadow-success/60",
  RETRYING: "bg-warning shadow-[0_0_12px] shadow-warning/50",
  FAILED: "bg-destructive shadow-[0_0_12px] shadow-destructive/60",
};

/**
 * Vertical attempt history. A compact take on Aceternity's Timeline: a gradient beam joins the
 * attempts, and the gap between them shows the back-off that was applied.
 */
export function AttemptTimeline({ attempts }: { attempts: DeliveryAttempt[] }) {
  return (
    <ol className="relative space-y-6 pl-8">
      <span
        aria-hidden
        className="absolute top-2 bottom-2 left-[11px] w-px bg-gradient-to-b from-primary/70 via-border to-transparent"
      />
      {attempts.map((attempt, i) => {
        const previous = attempts[i - 1];
        const gap = previous ? new Date(attempt.startedAt).getTime() - new Date(previous.startedAt).getTime() : null;
        return (
          <li key={attempt.id} className="relative">
            <span className={cn("absolute top-1.5 -left-[25px] size-2.5 rounded-full ring-4 ring-background", RAIL[attempt.outcome])} />
            {gap !== null && (
              <div className="mb-2 text-[11px] text-muted-foreground">waited {ms(gap)} (back-off)</div>
            )}
            <div className="rounded-lg border border-border bg-card p-4">
              <div className="flex flex-wrap items-center gap-3">
                <span className="font-mono text-xs text-muted-foreground">#{attempt.attemptNumber}</span>
                <StatusBadge status={attempt.outcome} />
                <HttpStatus code={attempt.statusCode} />
                <span className="font-mono text-xs text-muted-foreground">{ms(attempt.durationMs)}</span>
                <span className="ml-auto text-xs text-muted-foreground">{dateTime(attempt.startedAt)}</span>
              </div>
              {attempt.error && <p className="mt-2 text-sm text-destructive">{attempt.error}</p>}
              {attempt.responseExcerpt && (
                <pre className="mt-3 max-h-32 overflow-auto rounded-md bg-background p-3 font-mono text-xs text-muted-foreground">
                  {attempt.responseExcerpt}
                </pre>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
