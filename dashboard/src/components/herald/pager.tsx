"use client";

import { useState } from "react";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { Button } from "@/components/ui/button";

/** Keeps a stack of cursors so cursor-paginated lists can go back as well as forward. */
export function useCursor() {
  const [stack, setStack] = useState<string[]>([]);
  return {
    cursor: stack.at(-1) ?? null,
    page: stack.length + 1,
    next: (cursor: string) => setStack((s) => [...s, cursor]),
    previous: () => setStack((s) => s.slice(0, -1)),
    reset: () => setStack([]),
  };
}

export function Pager({ page, nextCursor, onNext, onPrevious }: { page: number; nextCursor: string | null; onNext: (c: string) => void; onPrevious: () => void }) {
  return (
    <div className="flex items-center justify-end gap-2 text-sm text-muted-foreground">
      <span>Page {page}</span>
      <Button variant="outline" size="icon" disabled={page === 1} onClick={onPrevious} aria-label="Previous page">
        <IconChevronLeft className="size-4" />
      </Button>
      <Button variant="outline" size="icon" disabled={!nextCursor} onClick={() => nextCursor && onNext(nextCursor)} aria-label="Next page">
        <IconChevronRight className="size-4" />
      </Button>
    </div>
  );
}
