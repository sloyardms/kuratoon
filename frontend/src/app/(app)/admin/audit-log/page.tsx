import { Suspense } from "react";

import { AuditLog } from "./audit-log";

export default function AuditLogPage() {
  return (
    <Suspense>
      <AuditLog />
    </Suspense>
  );
}
