"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { IconSearch, IconStack2 } from "@tabler/icons-react";
import { EmptyState } from "@/components/herald/empty-state";
import { PageHeader } from "@/components/herald/page-header";
import { Pager, useCursor } from "@/components/herald/pager";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useApi } from "@/lib/api";
import { dateTime, shortId, timeAgo } from "@/lib/format";
import type { HeraldEvent, Page } from "@/lib/types";

export default function EventsPage() {
  const router = useRouter();
  const [type, setType] = useState("");
  const cursor = useCursor();
  const query = new URLSearchParams({ limit: "25" });
  if (type) query.set("type", type);
  if (cursor.cursor) query.set("before", cursor.cursor);
  const { data } = useApi<Page<HeraldEvent>>(`/v1/events?${query}`, { refreshInterval: 5_000 });

  return (
    <>
      <PageHeader title="Events" description="Every business event your systems published, newest first." />
      <div className="relative max-w-sm">
        <IconSearch className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          className="pl-9 font-mono"
          placeholder="Filter by exact type, e.g. claim.created"
          value={type}
          onChange={(e) => {
            setType(e.target.value.trim());
            cursor.reset();
          }}
        />
      </div>
      {!data ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : !data.data.length ? (
        <EmptyState icon={<IconStack2 className="size-5" />} title="No events" description="Publish one from the Playground or POST /v1/events." />
      ) : (
        <>
          <Card className="py-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">Event</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Payload</TableHead>
                  <TableHead className="text-right pr-6">Published</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.data.map((event) => (
                  <TableRow key={event.id} className="cursor-pointer" onClick={() => router.push(`/events/${event.id}`)}>
                    <TableCell className="pl-6 font-mono text-xs text-muted-foreground">evt_{shortId(event.id)}</TableCell>
                    <TableCell><Badge variant="secondary" className="font-mono text-[11px]">{event.type}</Badge></TableCell>
                    <TableCell className="max-w-md truncate font-mono text-xs text-muted-foreground">{JSON.stringify(event.payload)}</TableCell>
                    <TableCell className="pr-6 text-right text-muted-foreground" title={dateTime(event.createdAt)}>{timeAgo(event.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
          <Pager page={cursor.page} nextCursor={data.nextCursor} onNext={cursor.next} onPrevious={cursor.previous} />
        </>
      )}
    </>
  );
}
