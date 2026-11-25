"use client";

import { useState } from "react";
import Link from "next/link";
import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { IconAlertOctagon, IconBolt, IconCircleCheck, IconClock } from "@tabler/icons-react";
import { PageHeader } from "@/components/herald/page-header";
import { StatCard } from "@/components/herald/stat-card";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useApi } from "@/lib/api";
import { compact, ms, percent, timeAgo } from "@/lib/format";
import type { DeliveryStatus, Stats } from "@/lib/types";
import { cn } from "@/lib/utils";

const WINDOWS = { "1h": 1, "24h": 24, "7d": 168 } as const;
type Window = keyof typeof WINDOWS;

const chartConfig = {
  succeeded: { label: "Succeeded", color: "var(--chart-1)" },
  failed: { label: "Failed", color: "var(--chart-2)" },
} satisfies ChartConfig;

const STATUS_COLORS: Record<DeliveryStatus, string> = {
  SUCCEEDED: "bg-success",
  PENDING: "bg-warning",
  QUEUED: "bg-primary",
  DELIVERING: "bg-primary/60",
  DEAD: "bg-destructive",
};

export default function OverviewPage() {
  const [window, setWindow] = useState<Window>("24h");
  const { data: stats } = useApi<Stats>(`/v1/stats?hours=${WINDOWS[window]}`, { refreshInterval: 10_000 });

  return (
    <>
      <PageHeader
        title="Overview"
        description="Delivery health across every endpoint. Refreshes every 10 seconds."
        actions={
          <Tabs value={window} onValueChange={(v) => setWindow(v as Window)}>
            <TabsList>
              {Object.keys(WINDOWS).map((w) => (
                <TabsTrigger key={w} value={w}>
                  {w}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        }
      />

      {!stats ? (
        <div className="grid gap-4 md:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Events published" value={compact(stats.events)} icon={<IconBolt className="size-4" />}
              hint={`${compact(stats.attempts.total)} delivery attempts`} />
            <StatCard label="Success rate" value={percent(stats.attempts.successRate)} icon={<IconCircleCheck className="size-4" />}
              tone={stats.attempts.successRate >= 0.99 ? "success" : stats.attempts.successRate >= 0.9 ? "warning" : "danger"}
              hint={`${compact(stats.attempts.failed)} failed attempts`} />
            <StatCard label="p95 latency" value={ms(stats.latencyMs.p95)} icon={<IconClock className="size-4" />}
              hint={`p50 ${ms(stats.latencyMs.p50)} · p99 ${ms(stats.latencyMs.p99)}`} />
            <StatCard label="Dead-lettered" value={compact(stats.deliveriesByStatus.DEAD)} icon={<IconAlertOctagon className="size-4" />}
              tone={stats.deliveriesByStatus.DEAD > 0 ? "danger" : "default"}
              hint={<Link href="/deliveries?status=DEAD" className="underline-offset-4 hover:underline">Review and replay →</Link>} />
          </div>

          <div className="grid gap-4 xl:grid-cols-3 [&>*]:min-w-0">
            <Card className="xl:col-span-2">
              <CardHeader>
                <CardTitle>Delivery attempts</CardTitle>
                <CardDescription>Succeeded vs. failed, bucketed every {humanBucket(stats.bucket)}</CardDescription>
              </CardHeader>
              <CardContent>
                <ChartContainer config={chartConfig} className="h-64 w-full">
                  <AreaChart data={stats.series} margin={{ left: 0, right: 8 }}>
                    <defs>
                      {(["succeeded", "failed"] as const).map((key) => (
                        <linearGradient key={key} id={`fill-${key}`} x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor={`var(--color-${key})`} stopOpacity={0.35} />
                          <stop offset="95%" stopColor={`var(--color-${key})`} stopOpacity={0} />
                        </linearGradient>
                      ))}
                    </defs>
                    <CartesianGrid vertical={false} strokeOpacity={0.08} />
                    <XAxis dataKey="start" tickLine={false} axisLine={false} minTickGap={32}
                      tickFormatter={(v: string) => new Date(v).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })} />
                    <YAxis tickLine={false} axisLine={false} width={32} allowDecimals={false} />
                    <ChartTooltip content={<ChartTooltipContent indicator="dot" labelFormatter={(v) => new Date(v as string).toLocaleString("en-IN")} />} />
                    <Area dataKey="succeeded" type="monotone" stroke="var(--color-succeeded)" fill="url(#fill-succeeded)" strokeWidth={2} stackId="a" />
                    <Area dataKey="failed" type="monotone" stroke="var(--color-failed)" fill="url(#fill-failed)" strokeWidth={2} stackId="a" />
                  </AreaChart>
                </ChartContainer>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Delivery states</CardTitle>
                <CardDescription>Deliveries created in this window</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <StatusBar counts={stats.deliveriesByStatus} />
                <ul className="space-y-2 text-sm">
                  {(Object.keys(STATUS_COLORS) as DeliveryStatus[]).map((status) => (
                    <li key={status} className="flex items-center justify-between">
                      <Link href={`/deliveries?status=${status}`} className="flex items-center gap-2 text-muted-foreground hover:text-foreground">
                        <span className={cn("size-2 rounded-full", STATUS_COLORS[status])} />
                        {status.toLowerCase()}
                      </Link>
                      <span className="font-mono tabular-nums">{stats.deliveriesByStatus[status]}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Endpoint health</CardTitle>
              <CardDescription>Success rate per endpoint in this window. Circuits open automatically for failing endpoints.</CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Endpoint</TableHead>
                    <TableHead className="text-right">Attempts</TableHead>
                    <TableHead className="w-56">Success rate</TableHead>
                    <TableHead className="text-right">Dead</TableHead>
                    <TableHead className="text-right">Last attempt</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {stats.endpoints.map((e) => (
                    <TableRow key={e.id}>
                      <TableCell className="max-w-sm truncate font-mono text-xs">
                        <Link href={`/deliveries?endpointId=${e.id}`} className="hover:underline">{e.url}</Link>
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">{e.attempts}</TableCell>
                      <TableCell><HealthBar rate={e.successRate} /></TableCell>
                      <TableCell className={cn("text-right font-mono tabular-nums", e.dead > 0 && "text-destructive")}>{e.dead}</TableCell>
                      <TableCell className="text-right text-muted-foreground">{timeAgo(e.lastAttemptAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}
    </>
  );
}

function StatusBar({ counts }: { counts: Record<DeliveryStatus, number> }) {
  const total = Object.values(counts).reduce((a, b) => a + b, 0) || 1;
  return (
    <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-muted">
      {(Object.keys(STATUS_COLORS) as DeliveryStatus[]).map((s) => (
        <div key={s} className={STATUS_COLORS[s]} style={{ width: `${(counts[s] / total) * 100}%` }} />
      ))}
    </div>
  );
}

function HealthBar({ rate }: { rate: number }) {
  const tone = rate >= 0.99 ? "bg-success" : rate >= 0.8 ? "bg-warning" : "bg-destructive";
  return (
    <div className="flex items-center gap-3">
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
        <div className={cn("h-full rounded-full", tone)} style={{ width: `${rate * 100}%` }} />
      </div>
      <span className="w-12 text-right font-mono text-xs tabular-nums">{percent(rate)}</span>
    </div>
  );
}

/** Formats an ISO-8601 duration such as "PT1H" or "PT3M". */
function humanBucket(iso: string) {
  const [, hours, minutes] = /PT(?:(\d+)H)?(?:(\d+)M)?/.exec(iso) ?? [];
  const total = Number(hours ?? 0) * 60 + Number(minutes ?? 0);
  return total >= 60 ? `${total / 60} h` : `${total} min`;
}
