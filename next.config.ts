import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // Builds must fail on TypeScript errors; hiding them can publish a broken app.
  reactStrictMode: true,
};

export default nextConfig;
