# -*- coding: utf-8 -*-
"""
Foggy MCP Connection Test Result Dialog

Transient model to display connection test results in a modal dialog.
"""
import logging

from odoo import fields, models

_logger = logging.getLogger(__name__)


class FoggyConnectionTestResult(models.TransientModel):
    _name = 'foggy.connection.test.result'
    _description = 'Foggy MCP Connection Test Result'

    status_message = fields.Text(
        string='Connection Result',
        readonly=True,
    )
