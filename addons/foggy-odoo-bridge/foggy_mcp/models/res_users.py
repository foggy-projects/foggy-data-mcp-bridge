# -*- coding: utf-8 -*-
"""
Extend res.users to auto-create a Foggy MCP API key on first login.

This ensures every Odoo user who logs in can immediately use
MCP-enabled AI tools without manual key provisioning.
"""
import logging

from odoo import api, models

_logger = logging.getLogger(__name__)


class ResUsers(models.Model):
    _inherit = 'res.users'

    @classmethod
    def _login(cls, db, login, password, user_agent_env=None):
        """Override login to auto-provision a Foggy MCP API key."""
        uid = super()._login(db, login, password, user_agent_env=user_agent_env)
        if uid:
            try:
                with cls.pool.cursor() as cr:
                    env = api.Environment(cr, uid, {})
                    env['foggy.api.key'].ensure_user_key(uid)
            except Exception:
                # Never block login if key creation fails
                _logger.warning(
                    "Failed to auto-create Foggy MCP API key for user %s",
                    login, exc_info=True,
                )
        return uid
