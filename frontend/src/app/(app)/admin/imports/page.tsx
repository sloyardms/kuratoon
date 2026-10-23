import { Suspense } from "react";

import { ImportsAdmin } from "./imports-admin";

export default function ImportsPage() {
  return (
    <Suspense>
      <ImportsAdmin />
    </Suspense>
  );
}
