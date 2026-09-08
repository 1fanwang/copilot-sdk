import { PassThrough } from "node:stream";
import { describe, expect, it, onTestFinished } from "vitest";
import { createMessageConnection } from "vscode-jsonrpc/node.js";
import { CopilotSession } from "../src/session.js";
import type { MessageOptions } from "../src/types.js";

describe.each(["send", "sendAndWait"] as const)("%s message provenance", (method) => {
    for (const mode of [undefined, "enqueue", "immediate"] as const) {
        it.each([undefined, "agent-sender-id"])(
            `preserves source %s and other options with mode ${mode}`,
            async (source) => {
                const requests = new PassThrough();
                const responses = new PassThrough();
                const connection = createMessageConnection(responses, requests);
                const server = createMessageConnection(requests, responses);
                const session = new CopilotSession("session-1", connection);
                onTestFinished(() => {
                    connection.dispose();
                    server.dispose();
                    requests.destroy();
                    responses.destroy();
                });

                const options: MessageOptions = {
                    prompt: "Agent update",
                    source,
                    mode,
                    agentMode: "plan",
                    attachments: [{ type: "file", path: "report.txt", displayName: "Report" }],
                    displayPrompt: "Update from sender",
                    requestHeaders: { "X-Custom-Tag": "value-1" },
                };
                let received: Record<string, unknown> | undefined;
                server.onRequest("session.send", (params: Record<string, unknown>) => {
                    received = params;
                    session._dispatchEvent({
                        type: "session.idle",
                        id: "00000000-0000-4000-8000-000000000001",
                        parentId: null,
                        timestamp: new Date().toISOString(),
                        ephemeral: true,
                        data: {},
                    });
                    return { messageId: "message-1" };
                });
                connection.listen();
                server.listen();

                const result = await session[method](options);
                expect(result).toBe(method === "send" ? "message-1" : undefined);
                expect(received).toEqual({
                    sessionId: "session-1",
                    prompt: options.prompt,
                    agentMode: options.agentMode,
                    attachments: options.attachments,
                    displayPrompt: options.displayPrompt,
                    requestHeaders: options.requestHeaders,
                    ...(mode === undefined ? {} : { mode }),
                    ...(source === undefined ? {} : { source }),
                });
                if (source === undefined) {
                    expect(received).not.toHaveProperty("source");
                }
            }
        );
    }
});
