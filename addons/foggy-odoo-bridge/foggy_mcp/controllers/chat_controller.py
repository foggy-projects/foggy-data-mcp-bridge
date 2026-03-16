# -*- coding: utf-8 -*-
"""
Foggy AI Chat Controller

Provides HTTP endpoints for the embedded AI chat feature.
Uses Odoo session authentication (cookie-based, for logged-in users).
"""
import json
import logging

from odoo import http
from odoo.http import request, Response

_logger = logging.getLogger(__name__)


class FoggyChatController(http.Controller):

    def _json_response(self, data, status=200):
        return Response(
            json.dumps(data, ensure_ascii=False, default=str),
            status=status,
            content_type='application/json; charset=utf-8',
        )

    @http.route('/foggy-mcp/chat/send', type='http', auth='user', methods=['POST'], csrf=False)
    def chat_send(self, **kwargs):
        """Send a message and get AI response."""
        try:
            body = request.get_json_data()
        except Exception:
            return self._json_response({'error': 'Invalid JSON'}, 400)

        message = body.get('message', '').strip()
        session_id = body.get('session_id')

        if not message:
            return self._json_response({'error': 'Empty message'}, 400)

        from ..services import llm_service
        result = llm_service.chat(
            request.env, request.uid, session_id, message,
        )
        return self._json_response(result)

    @http.route('/foggy-mcp/chat/sessions', type='http', auth='user', methods=['GET'], csrf=False)
    def chat_sessions(self, **kwargs):
        """List current user's chat sessions."""
        sessions = request.env['foggy.chat.session'].search([
            ('user_id', '=', request.uid),
        ], order='write_date desc', limit=50)

        data = [{
            'id': s.id,
            'name': s.name,
            'message_count': s.message_count,
            'write_date': s.write_date.isoformat() if s.write_date else None,
        } for s in sessions]

        return self._json_response({'sessions': data})

    @http.route('/foggy-mcp/chat/messages/<int:session_id>', type='http', auth='user', methods=['GET'], csrf=False)
    def chat_messages(self, session_id, **kwargs):
        """Get messages for a specific session."""
        session = request.env['foggy.chat.session'].search([
            ('id', '=', session_id),
            ('user_id', '=', request.uid),
        ], limit=1)

        if not session:
            return self._json_response({'error': 'Session not found'}, 404)

        messages = request.env['foggy.chat.message'].search([
            ('session_id', '=', session_id),
            ('role', 'in', ['user', 'assistant']),
        ], order='create_date asc, id asc')

        data = [{
            'id': m.id,
            'role': m.role,
            'content': m.content,
            'create_date': m.create_date.isoformat() if m.create_date else None,
        } for m in messages]

        return self._json_response({'messages': data})

    @http.route('/foggy-mcp/chat/sessions/<int:session_id>', type='http', auth='user', methods=['DELETE'], csrf=False)
    def chat_delete_session(self, session_id, **kwargs):
        """Delete a chat session."""
        session = request.env['foggy.chat.session'].search([
            ('id', '=', session_id),
            ('user_id', '=', request.uid),
        ], limit=1)

        if session:
            session.unlink()

        return self._json_response({'ok': True})
