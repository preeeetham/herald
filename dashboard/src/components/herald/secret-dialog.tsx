"use client";

import { IconAlertTriangle } from "@tabler/icons-react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { CopyButton } from "./copy-button";

/** Shows a credential exactly once, the way the API returns it. */
export function SecretDialog({
  title,
  description,
  secret,
  onClose,
}: {
  title: string;
  description: string;
  secret: string | null;
  onClose: () => void;
}) {
  return (
    <Dialog open={secret !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="rounded-lg border border-border bg-background p-3 font-mono text-sm break-all">{secret}</div>
        <p className="flex items-center gap-2 text-xs text-warning">
          <IconAlertTriangle className="size-4" /> Store it somewhere safe. You will not see it again.
        </p>
        <DialogFooter>
          {secret && <CopyButton value={secret} />}
          <Button onClick={onClose}>Done</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
