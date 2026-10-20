import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";

/** Offset pagination for admin tables (docs/spec/07-frontend.md). Pages are 0-based. */
export function Pagination({
  page,
  totalPages,
  totalItems,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalItems: number;
  onPage: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return <p className="text-sm text-muted-foreground">{totalItems} total</p>;
  }
  return (
    <nav
      aria-label="Pagination"
      className="flex items-center justify-between gap-2 text-sm"
    >
      <span className="text-muted-foreground">
        Page {page + 1} of {totalPages} · {totalItems} total
      </span>
      <div className="flex gap-1">
        <Button
          variant="outline"
          size="sm"
          disabled={page <= 0}
          onClick={() => onPage(page - 1)}
        >
          <ChevronLeft aria-hidden /> Previous
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={page >= totalPages - 1}
          onClick={() => onPage(page + 1)}
        >
          Next <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  );
}
