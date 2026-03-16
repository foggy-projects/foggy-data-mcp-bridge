/** @odoo-module */
import { Component, useState, useRef, onMounted, onWillUnmount } from "@odoo/owl";
import { registry } from "@web/core/registry";
import { useService } from "@web/core/utils/hooks";

/**
 * Foggy AI Chat — Full-page OWL component for LLM-powered data analysis.
 *
 * Registered as a client action: "foggy_mcp.foggy_chat_action_client"
 */
class FoggyChat extends Component {
    static template = "foggy_mcp.FoggyChat";
    static props = ["*"];

    setup() {
        this.rpc = useService("rpc");
        this.notification = useService("notification");

        this.state = useState({
            sessions: [],
            currentSessionId: null,
            messages: [],
            inputText: "",
            loading: false,
            sidebarOpen: true,
        });

        this.messagesEndRef = useRef("messagesEnd");
        this.inputRef = useRef("chatInput");

        onMounted(() => {
            this.loadSessions();
            if (this.inputRef.el) {
                this.inputRef.el.focus();
            }
        });
    }

    async loadSessions() {
        try {
            const resp = await fetch("/foggy-mcp/chat/sessions", {
                headers: { "Content-Type": "application/json" },
            });
            const data = await resp.json();
            this.state.sessions = data.sessions || [];
        } catch (e) {
            console.error("Failed to load sessions:", e);
        }
    }

    async loadMessages(sessionId) {
        if (!sessionId) return;
        try {
            const resp = await fetch(`/foggy-mcp/chat/messages/${sessionId}`, {
                headers: { "Content-Type": "application/json" },
            });
            const data = await resp.json();
            this.state.messages = data.messages || [];
            this.state.currentSessionId = sessionId;
            this.scrollToBottom();
        } catch (e) {
            console.error("Failed to load messages:", e);
        }
    }

    async sendMessage() {
        const text = this.state.inputText.trim();
        if (!text || this.state.loading) return;

        // Optimistic UI update
        this.state.messages.push({
            role: "user",
            content: text,
            create_date: new Date().toISOString(),
        });
        this.state.inputText = "";
        this.state.loading = true;
        this.scrollToBottom();

        try {
            const resp = await fetch("/foggy-mcp/chat/send", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    message: text,
                    session_id: this.state.currentSessionId,
                }),
            });
            const data = await resp.json();

            if (data.error) {
                this.state.messages.push({
                    role: "assistant",
                    content: `⚠️ ${data.error}`,
                    create_date: new Date().toISOString(),
                });
            } else {
                // Update session ID (might be newly created)
                if (data.session_id && !this.state.currentSessionId) {
                    this.state.currentSessionId = data.session_id;
                    this.loadSessions();
                }
                this.state.messages.push({
                    role: "assistant",
                    content: data.content,
                    create_date: new Date().toISOString(),
                });
            }
        } catch (e) {
            this.state.messages.push({
                role: "assistant",
                content: `⚠️ Network error: ${e.message}`,
                create_date: new Date().toISOString(),
            });
        } finally {
            this.state.loading = false;
            this.scrollToBottom();
        }
    }

    onKeydown(ev) {
        if (ev.key === "Enter" && !ev.shiftKey) {
            ev.preventDefault();
            this.sendMessage();
        }
    }

    newChat() {
        this.state.currentSessionId = null;
        this.state.messages = [];
        if (this.inputRef.el) {
            this.inputRef.el.focus();
        }
    }

    selectSession(sessionId) {
        this.loadMessages(sessionId);
    }

    async deleteSession(ev, sessionId) {
        ev.stopPropagation();
        try {
            await fetch(`/foggy-mcp/chat/sessions/${sessionId}`, { method: "DELETE" });
            this.state.sessions = this.state.sessions.filter(s => s.id !== sessionId);
            if (this.state.currentSessionId === sessionId) {
                this.newChat();
            }
        } catch (e) {
            console.error("Delete failed:", e);
        }
    }

    toggleSidebar() {
        this.state.sidebarOpen = !this.state.sidebarOpen;
    }

    scrollToBottom() {
        setTimeout(() => {
            if (this.messagesEndRef.el) {
                this.messagesEndRef.el.scrollIntoView({ behavior: "smooth" });
            }
        }, 50);
    }

    /**
     * Simple markdown-like rendering for assistant messages.
     * Handles: **bold**, `code`, ```code blocks```, and tables.
     */
    formatContent(content) {
        if (!content) return "";
        // Escape HTML
        let html = content
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");

        // Code blocks (```...```)
        html = html.replace(/```(\w*)\n([\s\S]*?)```/g,
            '<pre class="foggy-code-block"><code>$2</code></pre>');
        // Inline code
        html = html.replace(/`([^`]+)`/g, '<code class="foggy-inline-code">$1</code>');
        // Bold
        html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
        // Tables (pipe-delimited)
        html = html.replace(
            /(\|.+\|\n)((?:\|[-:]+)+\|)\n((?:\|.+\|\n?)+)/g,
            (match, header, separator, body) => {
                const headers = header.trim().split("|").filter(Boolean).map(h => `<th>${h.trim()}</th>`);
                const rows = body.trim().split("\n").map(row => {
                    const cells = row.split("|").filter(Boolean).map(c => `<td>${c.trim()}</td>`);
                    return `<tr>${cells.join("")}</tr>`;
                });
                return `<table class="foggy-table"><thead><tr>${headers.join("")}</tr></thead><tbody>${rows.join("")}</tbody></table>`;
            }
        );
        // Line breaks
        html = html.replace(/\n/g, "<br/>");
        return html;
    }
}

registry.category("actions").add("foggy_mcp.foggy_chat_action_client", FoggyChat);
