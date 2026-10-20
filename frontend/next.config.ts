import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Self-contained server bundle for the Docker image.
  output: "standalone",
  // The backend already produces image size variants; never re-optimize.
  images: { unoptimized: true },
  cacheComponents: true,
  partialPrefetching: true,
  // In production nginx routes /api to the backend. `next dev` has no nginx, so forward it here.
  async rewrites() {
    if (process.env.NODE_ENV !== "development") return [];
    const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080";
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
