# -*- coding: utf-8 -*-
import logging
import secrets
import string

import markupsafe

from odoo import api, fields, models

_logger = logging.getLogger(__name__)

# API Key prefix for easy identification
API_KEY_PREFIX = 'fmcp_'
API_KEY_LENGTH = 40


def _generate_api_key():
    """Generate a secure random API key with prefix."""
    alphabet = string.ascii_letters + string.digits
    random_part = ''.join(secrets.choice(alphabet) for _ in range(API_KEY_LENGTH))
    return f"{API_KEY_PREFIX}{random_part}"


class FoggyApiKey(models.Model):
    _name = 'foggy.api.key'
    _description = 'Foggy MCP API Key'
    _order = 'create_date desc'

    name = fields.Char(
        string='Description',
        required=True,
        help='A human-readable name for this API key (e.g., "Claude Desktop - Work Laptop")',
    )
    key = fields.Char(
        string='API Key',
        readonly=True,
        copy=False,
        help='The API key token. Auto-generated on creation.',
    )
    user_id = fields.Many2one(
        'res.users',
        string='User',
        required=True,
        default=lambda self: self.env.uid,
        ondelete='cascade',
        help='The Odoo user this API key authenticates as.',
    )
    active = fields.Boolean(
        string='Active',
        default=True,
        help='Deactivate to revoke access without deleting the key.',
    )
    last_used = fields.Datetime(
        string='Last Used',
        readonly=True,
        help='Timestamp of the last successful authentication with this key.',
    )
    company_ids = fields.Many2many(
        'res.company',
        string='Allowed Companies',
        help='If set, restrict this key to specific companies. Leave empty for all user companies.',
    )
    key_short = fields.Char(
        string='Key Preview',
        compute='_compute_key_short',
        help='Masked key preview (open record to copy full key).',
    )
    mcp_endpoint = fields.Char(
        string='MCP Endpoint',
        compute='_compute_mcp_config',
        help='Full MCP endpoint URL for this Odoo instance.',
    )
    mcp_config_html = fields.Html(
        string='MCP Configuration',
        compute='_compute_mcp_config',
        sanitize=False,
        help='Ready-to-copy MCP client configuration (formatted HTML).',
    )

    @api.depends('key')
    def _compute_key_short(self):
        for rec in self:
            if rec.key:
                # Show prefix + first 8 chars + mask
                rec.key_short = rec.key[:13] + '****'
            else:
                rec.key_short = ''

    _MCP_CONFIG_TEMPLATE = (
        '{\n'
        '  "mcpServers": {\n'
        '    "odoo": {\n'
        '      "url": "BASE_URL/foggy-mcp/rpc",\n'
        '      "headers": {\n'
        '        "Authorization": "Bearer API_KEY"\n'
        '      }\n'
        '    }\n'
        '  }\n'
        '}'
    )

    @api.depends('key')
    def _compute_mcp_config(self):
        base_url = self.env['ir.config_parameter'].sudo().get_param(
            'web.base.url', 'http://localhost:8069'
        )
        endpoint = base_url.rstrip('/') + '/foggy-mcp/rpc'
        pre_style = (
            'background:#f8f9fa; padding:12px; border-radius:4px; '
            'font-size:13px; line-height:1.6; margin:0;'
        )
        for rec in self:
            rec.mcp_endpoint = endpoint
            if rec.key:
                json_text = self._MCP_CONFIG_TEMPLATE.replace(
                    'BASE_URL', base_url.rstrip('/'),
                ).replace(
                    'API_KEY', rec.key,
                )
                escaped = markupsafe.escape(json_text)
                rec.mcp_config_html = markupsafe.Markup(
                    '<pre style="%s">%s</pre>'
                ) % (pre_style, escaped)
            else:
                rec.mcp_config_html = ''

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if not vals.get('key'):
                vals['key'] = _generate_api_key()
        records = super().create(vals_list)
        return records

    def action_regenerate_key(self):
        """Regenerate the API key."""
        self.ensure_one()
        self.key = _generate_api_key()
        _logger.info("API key regenerated for user %s (key: %s)", self.user_id.login, self.id)
        return {
            'type': 'ir.actions.client',
            'tag': 'display_notification',
            'params': {
                'title': 'API Key Regenerated',
                'message': f'New key: {self.key}',
                'type': 'success',
                'sticky': True,
            }
        }

    @api.model
    def ensure_user_key(self, user_id):
        """
        Ensure that a user has at least one active API key.
        Auto-creates one if none exists.

        Args:
            user_id: res.users record ID

        Returns:
            foggy.api.key record (existing or newly created)
        """
        existing = self.sudo().search([
            ('user_id', '=', user_id),
            ('active', '=', True),
        ], limit=1)
        if existing:
            return existing

        user = self.env['res.users'].sudo().browse(user_id)
        new_key = self.sudo().create({
            'name': f'Auto — {user.name}',
            'user_id': user_id,
        })
        _logger.info(
            "Auto-created Foggy MCP API key for user %s (uid=%s, key_id=%s)",
            user.login, user_id, new_key.id,
        )
        return new_key

    @api.model
    def authenticate_by_key(self, key):
        """
        Authenticate a request by API key.

        Args:
            key: The API key (with or without 'Bearer ' prefix)

        Returns:
            res.users record if valid, False otherwise
        """
        if not key:
            return False

        # Strip 'Bearer ' prefix
        if key.startswith('Bearer '):
            key = key[7:]

        if not key.startswith(API_KEY_PREFIX):
            return False

        api_key = self.sudo().search([
            ('key', '=', key),
            ('active', '=', True),
        ], limit=1)

        if not api_key:
            _logger.warning("Invalid or inactive API key attempted: %s...", key[:12])
            return False

        # Update last_used timestamp
        api_key.sudo().write({'last_used': fields.Datetime.now()})
        _logger.debug("API key authenticated: user=%s, key_id=%s", api_key.user_id.login, api_key.id)
        return api_key.user_id
