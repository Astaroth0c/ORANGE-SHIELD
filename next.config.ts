import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // TypeScript errors must fail the build instead of being silently ignored.
  reactStrictMode: false,
};

export default nextConfig;
