"use client";

import Link from "next/link";
import { IconArrowLeft } from "@tabler/icons-react";
import { PageHeader } from "@/components/herald/page-header";
import { HttpStatus, StatusBadge } from "@/components/herald/status-badge";
import { CodeBlock } from "@/components/ui/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useApi } from "@/lib/api";
import { dateTime, timeAgo } from "@/lib/format";
import type { EventDetail } from "@/lib/types";

export function EventView({ id }: { id: string }) {
  const { data: event } = useApi<EventDetail>(`/v1/events/${id}`, { refreshInterval: 3_000 });

  if (!event) return <Skeleton className="h-96 rounded-xl" />;
  const envelope = JSON.stringify({ id: event.id, type: event.type, timestamp: event.createdAt, data: event.payload }, null, 2);

  return (
    <>
      <Link href="/events" className="flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <IconArrowLeft className="size-4" /> Events
      </Link>
      <PageHeader title={event.type} description={`Published ${dateTime(event.createdAt)} · ${event.id}`} />
      <div className="grid gap-6 xl:grid-cols-5 [&>*]:min-w-0">
        <div className="xl:col-span-2">
          <CodeBlock language="json" filename="webhook body (Standard Webhooks envelope)" code={envelope} />
        </div>
        <Card className="xl:col-span-3">
          <CardHeader>
            <CardTitle>Deliveries ({event.deliveries.length})</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Endpoint</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Attempts</TableHead>
                  <TableHead className="text-right">Last code</TableHead>
                  <TableHead className="text-right">Updated</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {event.deliveries.map((d) => (
                  <TableRow key={d.id}>
                    <TableCell className="max-w-56 truncate font-mono text-xs">
                      <Link href={`/deliveries/${d.id}`} className="hover:underline">{d.endpointUrl}</Link>
                    </TableCell>
                    <TableCell><StatusBadge status={d.status} /></TableCell>
                    <TableCell className="text-right font-mono tabular-nums">{d.attemptCount}</TableCell>
                    <TableCell className="text-right"><HttpStatus code={d.lastStatusCode} /></TableCell>
                    <TableCell className="text-right text-muted-foreground">{timeAgo(d.updatedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
