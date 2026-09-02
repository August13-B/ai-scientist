/// <reference types="@cloudflare/workers-types" />
declare global { namespace Cloudflare { interface Env { DB: D1Database } } }
export {};
