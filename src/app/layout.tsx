import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/toaster";
import { Providers } from "@/components/providers";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Orange Shield — Bloqueio Inteligente de Chamadas e SMS",
  description:
    "Proteção inteligente contra telemarketing, robocalls, golpes telefônicos e SMS comercial de operadoras. SmartCall Engine com pontuação de risco, listas autorizadas e inteligência coletiva.",
  keywords: [
    "bloqueio de chamadas",
    "telemarketing",
    "robocall",
    "SMS",
    "anti-spam",
    "proteção telefônica",
    "Orange Shield",
  ],
  icons: {
    icon: "/logo.svg",
  },
};

export const viewport: Viewport = {
  themeColor: "#15130f",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR" className="dark" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased bg-background text-foreground`}
      >
        <Providers>{children}</Providers>
        <Toaster />
      </body>
    </html>
  );
}
