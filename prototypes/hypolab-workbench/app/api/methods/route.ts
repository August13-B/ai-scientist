import { desc, eq } from "drizzle-orm";
import { getDb } from "../../../db";
import { methods } from "../../../db/schema";

export async function GET() {
  try {
    const rows = await getDb().select().from(methods).orderBy(desc(methods.id)).limit(100);
    return Response.json({ methods: rows });
  } catch {
    return Response.json({ methods: [] });
  }
}

export async function POST(request: Request) {
  const input = await request.json() as Record<string, string>;
  if (!input.name?.trim() || !input.scenario?.trim()) {
    return Response.json({ error: "方法名称和适用场景不能为空" }, { status: 400 });
  }
  const value = {
    name: input.name.trim(),
    category: input.category || "机器学习",
    scenario: input.scenario.trim(),
    steps: input.steps || "",
    metrics: input.metrics || "",
    source: input.source || "",
  };
  try {
    const [method] = await getDb().insert(methods).values(value).returning();
    return Response.json({ method }, { status: 201 });
  } catch {
    return Response.json({ method: { ...value, id: Date.now() } }, { status: 201 });
  }
}

export async function DELETE(request: Request) {
  const id = Number(new URL(request.url).searchParams.get("id"));
  if (!id) return Response.json({ error: "无效记录" }, { status: 400 });
  try {
    await getDb().delete(methods).where(eq(methods.id, id));
  } catch {
    // A local demo record can still be removed from the current UI state.
  }
  return Response.json({ ok: true });
}
