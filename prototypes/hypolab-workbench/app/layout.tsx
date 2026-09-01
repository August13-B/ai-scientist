import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "HypoLab · 科研假设智能生成系统",
  description: "面向科研人员的方法知识库、科学假设生成与验证工作台。",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN"><body>{children}</body></html>;
}
